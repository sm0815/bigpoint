package de.smetzger.bigpoint.gasstation.greedy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;
import de.smetzger.bigpoint.gasstation.greedy.Customer.State;

/** a version of the gas station that tries to maximize the amount of gas sold */
public class StevesGreedyGasStation implements GasStation{
	
	//the pumps
	protected List<GasPump> pumps=new ArrayList<GasPump>();
	//and their attendants, this time sorted by gas type
	protected List<List<QueueingPumpAttendant>> attendants=
			new ArrayList<List<QueueingPumpAttendant>>(GasType.values().length);
	
	// statistics
	protected double revenue=0;
	protected AtomicInteger cancelledTooExpensive=new AtomicInteger();
	protected AtomicInteger cancelledAllOutaGas=new AtomicInteger();
	protected AtomicInteger sold=new AtomicInteger();
	protected double[] prices=new double[GasType.values().length];
	
	//sleep time between checks for the next customer
	protected final static int sleepTime=100;
	
	/** constructor */
	public StevesGreedyGasStation(){
		for(int i=0;i<prices.length;i++){
			prices[i]=0;
			attendants.add(new ArrayList<QueueingPumpAttendant>());
		}
	}
	

	/**
	 * Add a gas pump to this station.
	 * This is used to set up this station.
	 * 
	 * @param pump
	 *            the gas pump
	 */
	public void addGasPump(GasPump pump) {
		pumps.add(pump);	
		QueueingPumpAttendant attendant=new QueueingPumpAttendant(this,pump); //assigns an attendant
		attendants.get(pump.getGasType().ordinal()).add(attendant); //and sorts the attendant into the list for that gas type
		new Thread(attendant).start(); //starts the attendant's service cycle
	}

	/**
	 * Get all gas pumps that are currently associated with this gas station.
	 * 
	 * Modifying the resulting collection should not affect this gas station.
	 * 
	 * @return A collection of all gas pumps.
	 */
	public Collection<GasPump> getGasPumps() {
		return pumps;
	}

	/**
	 * Simulates a customer wanting to buy a specific amount of gas.
	 * 
	 * @param type
	 *            The type of gas the customer wants to buy
	 * @param amountInLiters
	 *            The amount of gas the customer wants to buy. Nothing less than this amount is acceptable!
	 * @param maxPricePerLiter
	 *            The maximum price the customer is willing to pay per liter
	 * @return the price the customer has to pay for this transaction
	 * @throws NotEnoughGasException
	 *             Should be thrown in case not enough gas of this type can be provided
	 *             by any single {@link GasPump}.
	 * @throws GasTooExpensiveException
	 *             Should be thrown if gas is not sold at the requested price (or any lower price)
	 */
	public double buyGas(GasType type, double amountInLiters,
			double maxPricePerLiter) throws NotEnoughGasException,
			GasTooExpensiveException {
		
		//repesent the request as a customer object
		Customer c=new Customer(type,amountInLiters,maxPricePerLiter);
		
		//match it to an attendant and fix the price
		Double price=queueAtMatchingPumpAttendant(c); //trows the exceptions if they apply
		
		//once we reach this point the customer is served and the gas was successfully sold
		sold.incrementAndGet();			
		double cost=price*amountInLiters;
		
		addRevenue(cost);
		return cost;
	}
	
	/** adds the cost of a gas purchase to the overall revenue */
	synchronized protected void addRevenue(double purchaseCost){
		revenue+=purchaseCost;
	}

	/** sorts the customer into the queue of a matching attendant if possible 
	 *  @return		the price that applies for this transaction
	 *  @throws NotEnoughGasException - if no pump available with enough gas 
	 *  @throws GasTooExpensiveException 
     */
	synchronized  protected double queueAtMatchingPumpAttendant(Customer c) 
			throws NotEnoughGasException, GasTooExpensiveException{
		
		//get and fix the price
		double currentPrice=getPrice(c.getGasType());
		
		//price check
		if(c.getMaxPricePaid()<currentPrice){
			cancelledTooExpensive.incrementAndGet();			
			throw new GasTooExpensiveException();
		}			
		
		boolean queued=false; //flag indicating whether customer could be matched to attendant
		//go over all attendants that serve the correct gas type
		for(QueueingPumpAttendant attendant:attendants.get(c.getGasType().ordinal())){
			queued=attendant.tryToQueueCustomer(c);
			if(queued) //if we find a match that can take the customer, we are done
				break;
		}
		
		//if we could not queue, we check if the customer is potentially servable 
		// i.e. if there is any gas pump that has enough gas left to serve him
		// IF we ignore the other customers queuing at the same pump/attendant
		if((!queued) && queuable(c))
			reorganizeQueues(c); //if that is the case we try to reorganize the queues in an optimal fashion
		
		// once we reach this point, the customer is either queued (in which case we wait)
		// or the customer is considered un-servable, in which case the state is 'CannotBeServed'
		while(c.getState()==State.InProcess){
			try {
				this.wait();
			} catch (InterruptedException e) {
				//handled by rechecking main condition of my customer
			}
		}
		
		//if we cannot serve the customer, it has to be because there is not enough gas, 
		// remember we did the price check explicitly earlier on
		if(c.getState()==State.CannotBeServed){
			cancelledAllOutaGas.incrementAndGet();
			throw new NotEnoughGasException();					
		}
		else if(c.getState()==State.Served){ // customer successfully served
			return currentPrice;
		}
		else throw new RuntimeException("Oopsy."); //this point should not be reached...
					
	}
	
	/** checks if there is any attendant/pump that could potentially serve the customer, 
	 * i.e. has enough gas for the customers request IF we ignore the other queued customers
	 * @param c		a customer
	 * @return 		true, if there is an attendant with a gas pump that has enough gas left; 
	 *              false otherwise
	 */
	protected boolean queuable(Customer c){
		boolean queueable=false;
		for (QueueingPumpAttendant attendant:attendants.get(c.getGasType().ordinal()))
			if(attendant.getRemainingAmount()>=c.getLitersWanted())
				queueable=true;
			
		if(!queueable)  //if there is no gas pump with enough gas to potentially serve the customer, it is unservable
			c.setState(State.CannotBeServed);
		return queueable;				
	}

	// just a comparator to order attendants by the amount of gas available in their respective gas pumps
	protected static final Comparator<QueueingPumpAttendant> gasBasedcomp=new RemainingGasBasedComparator();
	protected static class RemainingGasBasedComparator implements Comparator<QueueingPumpAttendant>{		 
	    @Override
	    public int compare(QueueingPumpAttendant o1, QueueingPumpAttendant o2) {
	    	return Double.compare(o1.getRemainingAmountAfterQueueProcessing(), o2.getRemainingAmountAfterQueueProcessing());	        
	    } //actually, at this point it should not matter which remaining value we use, because both should be equal...
	} 
	
	/** reorganize all the queues to try somehow getting the given customer into a queue;
	 *  uses an approximation heuristic; optimal solution could be achieved by investigating all possible combinations
	 *  @param misfit	a customer that could not be queued (but potentially could match an attendant)
	 * */
	protected void reorganizeQueues(Customer misfit){
		GasType type=misfit.getGasType(); //get the gas type, we only need to care about attendants for this type		
		Set<Customer> allCustomers=new HashSet<Customer>(); //will hold all customers queued at any attendant/pump and the misfit
		allCustomers.add(misfit);
		for(QueueingPumpAttendant a:attendants.get(type.ordinal())){
			allCustomers.addAll(a.emptyCustomerQueue());
		}
		
		//sort attendants by amount of gas left 
		Collections.sort(attendants.get(type.ordinal()), gasBasedcomp);		
		//for each attendant independently find an assignment of customers to its queue 
		//that maximizes the amount of gas sold at this pump/attendant
		//removes from allCustomers those that have been assigned to the attendant
		for(QueueingPumpAttendant attendant:attendants.get(type.ordinal()))
			findOptimalMatching(attendant,allCustomers);		

		//we assume any customer left cannot be served
		for(Customer c:allCustomers){
			c.setState(State.CannotBeServed);			
		}
		
	}
	
	/** very simple brute-force approach to find the best combination of customers that maximizes
	 *  the usage (in litres taken) at one gas-pump/attendant; 
	 *  we do not take the prize customers are willing to pay into account;
	 *  yet this would be a simple modification as this information is known to the station,
	 *  so it could be really greedy and prefer customers that are willing to pay more...
	 * @param attendant
	 * @param customers
	 */
	protected void findOptimalMatching(QueueingPumpAttendant attendant, Set<Customer> customers){
		//how much 'space' do we have
		double remainingLiters=attendant.getRemainingAmountAfterQueueProcessing();
	
		// basically generates all customer combinations that could be served by the current attendant
		// and then finds out the one with the maximal amount of litres
		for(int i=customers.size();i>0; i--){ //this whole process could be optimized...
			double maxValidLit=0;   //maximal amount of liters we could cover so far 
			Set<Customer> maxValidCombo=null; //customer combination that achieved this
			List<Set<Customer>> combinations=getAllCombinationsOfSize(customers,i);  //get all combinations of size i
			for(Set<Customer> combo:combinations){ //check for each combination
				double liters=0; 
				for(Customer c:combo)  //how much litres we need to satisfy all customers 
					liters+=c.getLitersWanted(); 
				if(liters<=remainingLiters)  // if the combination can be satisfied by the attendant
					if(liters>maxValidLit){  //and is larger than the last one
						maxValidLit=liters;  //remember it
						maxValidCombo=combo;
					}
			}
			
			if(maxValidCombo!=null){ // fi we found a combination generate the queue
				for(Customer c:maxValidCombo)
					attendant.tryToQueueCustomer(c);
				customers.removeAll(maxValidCombo);  //and ignore those customers for the remaining attendants
				return;
			}			
		}					
	}
	
	/** computes all subsets of a set of customers with a given size */
	protected static List<Set<Customer>> getAllCombinationsOfSize(Set<Customer> customers, int size){
		 List<Set<Customer>> combos=new ArrayList<Set<Customer>>();
		 combos.add(new HashSet<Customer>(customers));

		 for(int i=customers.size(); i>size;i--){
			 List<Set<Customer>> newCombos=new ArrayList<Set<Customer>>();
			 for(Set<Customer>combo:combos){
				 for(Customer c: combo){
					 Set<Customer> newCombo=new HashSet<Customer>(combo);
					 newCombo.remove(c);
					 newCombos.add(newCombo);
				 }
			 }
			 combos=newCombos;
		 }		 
		 
		 return combos;
	}
		
		
	
	
	/**
	 * @return the total revenue generated
	 */
	public double getRevenue() {
		return revenue;
	}

	/**
	 * Returns the number of successful sales. This should not include cancelled sales.
	 * 
	 * @return the number of sales that were successful
	 */
	public int getNumberOfSales() {
		return sold.get();
	}

	/**
	 * @return the number of cancelled transactions due to not enough gas being available
	 */
	public int getNumberOfCancellationsNoGas() {
		return cancelledAllOutaGas.get();
	}

	/**
	 * Returns the number of cancelled transactions due to the gas being more expensive than what the customer wanted to pay
	 * 
	 * @return the number of cancelled transactions
	 */
	public int getNumberOfCancellationsTooExpensive() {
		return cancelledTooExpensive.get();
	}

	
	/**
	 * Get the price for a specific type of gas
	 * 
	 * @param type
	 *            the type of gas
	 * @return the price per liter for this type of gas
	 */
	synchronized public double getPrice(GasType type) {
		return prices[type.ordinal()];
	}

	
	/**
	 * Set a new price for a specific type of gas
	 * 
	 * @param type
	 *            the type of gas
	 * @param price
	 *            the new price per liter for this type of gas
	 */
	synchronized public void setPrice(GasType type, double price) {
		prices[type.ordinal()]=price;		
	}

	
	public static void main(String [] args)
	{
		/** method for manual testing */
		Customer c1=new Customer(GasType.DIESEL, 4, 3);
		Customer c2=new Customer(GasType.DIESEL, 3, 3);
		Customer c3=new Customer(GasType.DIESEL, 2, 3);
		Customer c4=new Customer(GasType.DIESEL, 1, 3);
		
		Set<Customer> customers=new HashSet<Customer>();
		customers.add(c1);
		customers.add(c2);
		customers.add(c3);
		customers.add(c4);
//		List<Set<Customer>> combos=StevesGreedyGasStation.getAllCombinationsOfSize(customers, 2);
		
		/*for(Set<Customer> combo:combos)
			System.out.println(combo.toString());
		*/
		
		GasStation station=new StevesGreedyGasStation();
		station.addGasPump(new GasPump(GasType.DIESEL, 15));
		station.addGasPump(new GasPump(GasType.REGULAR, 150));
		station.addGasPump(new GasPump(GasType.SUPER, 15));
		station.addGasPump(new GasPump(GasType.SUPER, 10));
		
		station.setPrice(GasType.DIESEL, 2);
		station.setPrice(GasType.SUPER, 2);
		station.setPrice(GasType.REGULAR, 2);
		
		try {
			station.buyGas(GasType.DIESEL, 80, 2);
		} catch (NotEnoughGasException e) {
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {			
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 1);
		} catch (NotEnoughGasException e) {
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 5, 2);
		} catch (NotEnoughGasException e) {
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 4, 2);
		} catch (NotEnoughGasException e) {
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 5, 2);
		} catch (NotEnoughGasException e) {
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 2);
		} catch (NotEnoughGasException e) {
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {

			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 1);
		} catch (NotEnoughGasException e) {
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			e.printStackTrace();
		}
		
		System.out.println("Sold: "+station.getNumberOfSales());
		System.out.println("OutaGas: "+station.getNumberOfCancellationsNoGas());
		System.out.println("Expensive: "+station.getNumberOfCancellationsTooExpensive());
		System.out.println("Revenue: "+station.getRevenue());
	}
	
	
}
