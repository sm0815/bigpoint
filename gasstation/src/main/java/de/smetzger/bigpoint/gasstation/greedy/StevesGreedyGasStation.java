package de.smetzger.bigpoint.gasstation.greedy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.event.ListSelectionEvent;

import de.smetzger.bigpoint.gasstation.greedy.Customer.State;


import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

public class StevesGreedyGasStation implements GasStation{
	
	protected List<GasPump> pumps=new ArrayList<GasPump>();
	protected List<List<QueueingPumpAttendant>> attendants=new ArrayList<List<QueueingPumpAttendant>>(GasType.values().length);
	
	
	protected double revenue=0;
	protected AtomicInteger cancelledTooExpensive=new AtomicInteger();
	protected AtomicInteger cancelledAllOutaGas=new AtomicInteger();
	protected AtomicInteger sold=new AtomicInteger();
	protected double[] prices=new double[GasType.values().length];
	
	protected final static int sleepTime=100;
	
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
		QueueingPumpAttendant attendant=new QueueingPumpAttendant(this,pump);
		attendants.get(pump.getGasType().ordinal()).add(attendant);
		new Thread(attendant).start();
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
		
		
		Customer c=new Customer(type,amountInLiters,maxPricePerLiter);
		
		Double price=queueAtMatchingPumpAttendant(c);
		
		sold.incrementAndGet();			
		double cost=price*amountInLiters;
		
		addRevenue(cost);
		return cost;
	}
	
	/** adds the cost of a gas purchase to the overall revenue */
	synchronized protected void addRevenue(double purchaseCost){
		revenue+=purchaseCost;
	}

	/** tries to acquire a pump that has enough gas for the given requirements, 
	 *  @return a pump that can satisfy the customers needs 
	 *           or null if all matching pumps are busy handling other customers
	 *  @throws NotEnoughGasException - if no pump available with enough gas 
	 * @throws GasTooExpensiveException 
	 *  @Note: As GasPump is not thread-safe, this implementation may wait longer than 
	 *         strictly necessary before it throws a NotEnoughGasException;
	 *         still it will eventually throw the error, 
	 *         but may wait in some cases wait until all previous customers 
	 *         (for the same gas type) are dealt with */
	synchronized  protected double queueAtMatchingPumpAttendant(Customer c) 
			throws NotEnoughGasException, GasTooExpensiveException{
		
		double currentPrice=getPrice(c.getGasType());
		
		if(c.getMaxPricePaid()<currentPrice){
			cancelledTooExpensive.incrementAndGet();			
			throw new GasTooExpensiveException();
		}	
		
		boolean queued=false;
		for(QueueingPumpAttendant attendant:attendants.get(c.getGasType().ordinal())){
			queued=attendant.tryToQueueCustomer(c);
			System.out.println("c:"+c);
			System.out.println("queued:"+queued);
			if(queued)
				break;
		}
		
		if((!queued) && queuable(c))
			reorganizeQueues(c);
		
		while(c.getState()==State.InProcess){
			try {
				this.wait();
			} catch (InterruptedException e) {
				//handled by rechecking main condition of my customer
			}
		}
		
		if(c.getState()==State.CannotBeServed){
			cancelledAllOutaGas.incrementAndGet();
			throw new NotEnoughGasException();					
		}
		else if(c.getState()==State.Served){
			return currentPrice;
		}
		else throw new RuntimeException("Oopsy.");
					
	}
	
	protected boolean queuable(Customer c){
		boolean queueable=false;
		for (QueueingPumpAttendant attendant:attendants.get(c.getGasType().ordinal()))
			if(attendant.getRemainingAmount()>=c.getLitersWanted())
				queueable=true;
			
		if(!queueable)
			c.setState(State.CannotBeServed);
		return queueable;				
	}
	
	protected static final Comparator<QueueingPumpAttendant> gasBasedcomp=new RemainingGasBasedComparator();
	protected static class RemainingGasBasedComparator implements Comparator<QueueingPumpAttendant>{		 
	    @Override
	    public int compare(QueueingPumpAttendant o1, QueueingPumpAttendant o2) {
	    	return Double.compare(o1.getRemainingAmountAfterQueueProcessing(), o2.getRemainingAmountAfterQueueProcessing());	        
	    }
	} 
	
	protected void reorganizeQueues(Customer misfit){
		GasType type=misfit.getGasType();
		System.out.println("reorganizing");
		Set<Customer> allCustomers=new HashSet<Customer>();
		allCustomers.add(misfit);
		for(QueueingPumpAttendant a:attendants.get(type.ordinal())){
			allCustomers.addAll(a.emptyCustomerQueue());
		}
		
		Collections.sort(attendants.get(type.ordinal()), gasBasedcomp);		
		for(QueueingPumpAttendant attendant:attendants.get(type.ordinal()))
			findOptimalMatching(attendant,allCustomers);		
		
		for(Customer c:allCustomers){
			c.setState(State.CannotBeServed);
			System.out.println("cannot be served:"+c);
		}
		System.out.println("reorganized");
	}
	
	/** very simple brute-force approach to find the best combination of customers that maximizes
	 *  the usage (in liters taken) at one gas-pump; 
	 *  we do not take the prize customers are willing to pay into account;
	 *  yet this would be a simple modification as this information is known to the station,
	 *  so it could be really greedy and prefer customers that are willing to pay more...
	 * @param attendant
	 * @param customers
	 */
	protected void findOptimalMatching(QueueingPumpAttendant attendant, Set<Customer> customers){
		double remainingLiters=attendant.getRemainingAmountAfterQueueProcessing();
	
		for(int i=customers.size();i>0; i--){
			double maxValidLit=0;
			Set<Customer> maxValidCombo=null;		
			List<Set<Customer>> combinations=getAllCombinationsOfSize(customers,i);
			for(Set<Customer> combo:combinations){
				double liters=0;
				for(Customer c:combo)
					liters+=c.getLitersWanted();
				if(liters<=remainingLiters)
					if(liters>maxValidLit){
						maxValidLit=liters;
						maxValidCombo=combo;
					}
			}
			
			if(maxValidCombo!=null){
				for(Customer c:maxValidCombo)
					attendant.tryToQueueCustomer(c);
				customers.removeAll(maxValidCombo);
				return;
			}			
		}					
	}
	
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
		
		Customer c1=new Customer(GasType.DIESEL, 4, 3);
		Customer c2=new Customer(GasType.DIESEL, 3, 3);
		Customer c3=new Customer(GasType.DIESEL, 2, 3);
		Customer c4=new Customer(GasType.DIESEL, 1, 3);
		
		Set<Customer> customers=new HashSet<Customer>();
		customers.add(c1);
		customers.add(c2);
		customers.add(c3);
		customers.add(c4);
		List<Set<Customer>> combos=StevesGreedyGasStation.getAllCombinationsOfSize(customers, 2);
		
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 1);
		} catch (NotEnoughGasException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 5, 2);
		} catch (NotEnoughGasException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 4, 2);
		} catch (NotEnoughGasException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 5, 2);
		} catch (NotEnoughGasException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 2);
		} catch (NotEnoughGasException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 1);
		} catch (NotEnoughGasException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GasTooExpensiveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Sold: "+station.getNumberOfSales());
		System.out.println("OutaGas: "+station.getNumberOfCancellationsNoGas());
		System.out.println("Expensive: "+station.getNumberOfCancellationsTooExpensive());
		System.out.println("Revenue: "+station.getRevenue());
	}
	
	
}
