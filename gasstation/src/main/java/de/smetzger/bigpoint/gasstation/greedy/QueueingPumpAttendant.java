package de.smetzger.bigpoint.gasstation.greedy;



import java.util.ArrayList;
import java.util.List;

import de.smetzger.bigpoint.gasstation.greedy.Customer.State;

import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasType;

/** a version of the pump attendant that maintains a queue of customers to process.
 *  also takes care of keeping track of the remaining amount of gas, 
 *  including a prediction of the remaining amount at the point 
 *  when all customers in the queue will be served.  
 */
public class QueueingPumpAttendant implements Runnable{
	
	protected GasPump pump=null; //associated gas pump
	protected StevesGreedyGasStation station=null; //back-pointer to the gas station
	
	private double remaining; //remaining after currently pumping customer is done (or if none present, current amount)
	private double remainingAmountAfterQueueProcessing; //remaining after all queued customers are processed
	
	private List<Customer> queuedCustomers=new ArrayList<Customer>(); //list of queued customers
	
	public QueueingPumpAttendant (StevesGreedyGasStation station, GasPump pump){
		this.pump=pump;
		this.remaining=pump.getRemainingAmount();
		remainingAmountAfterQueueProcessing=remaining;
		this.station=station;
	}
	

	synchronized public boolean tryToQueueCustomer(Customer c){
//		System.out.println("Trying to queue "+c);
//		System.out.println("remaining: "+remainingAmountAfterQueueProcessing+"/"+remaining );
	
		//check whether we can serve the customer
		if(c.getGasType()!=getGasType())
			return false;
		if(getRemainingAmountAfterQueueProcessing()<c.getLitersWanted())
			return false;
		//add him to the queue and update remaning amount prediction
		queuedCustomers.add(c);
		remainingAmountAfterQueueProcessing-=c.getLitersWanted();
		return true;
	}
	
	
	public double getRemainingAmount() {
		return remaining;
	}

	
	public double getRemainingAmountAfterQueueProcessing() {
		return remainingAmountAfterQueueProcessing;
	}
	

	public GasType getGasType() {
		return pump.getGasType();
	}


	/** resets the customer queue and returns the customers queued up to now
	 *  @return list of currently queued customers */
	synchronized public List<Customer> emptyCustomerQueue(){
		List<Customer> customersQueued=queuedCustomers;
		queuedCustomers=new ArrayList<Customer>(GasType.values().length);
		remainingAmountAfterQueueProcessing=remaining;
		return customersQueued;
	}
	
	
	public void pumpGas(double amount) {
//		System.out.println("pumping "+amount+"l");
		pump.pumpGas(amount);
	}
	
	/** just checks if there are any customers */
	synchronized protected boolean noCustomers(){
		return queuedCustomers.isEmpty();
	}

	/** loops constantly looking for customers in its queue to process */
	@Override	
	public void run() {
		while(true){
			if(noCustomers())
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// ignore
				}
			else{
				processNextCustomer();
			}
		}
		
	}
	
	/** processes a customer, then notifies the station that this customer is done */
	protected void processNextCustomer(){
		Customer customer=pickCustomer(); //get the next one
		if(customer==null) //although unlikely, a reorganization might take place and leave an empty customer list
			return;
		pumpGas(customer.getLitersWanted());
		customer.setState(State.Served); //okay, we are done with this one
		notifyGasStation();		//let the station know
	}
	
	synchronized protected void notifyGasStation(){
		synchronized(station){
			station.notifyAll();
		}
	}
	

	/** retrieves the next customer from the queue */
	synchronized protected Customer pickCustomer(){
		if(queuedCustomers.isEmpty()) //check if the queue is empty 
			return null;
		Customer c=queuedCustomers.get(0);
		queuedCustomers.remove(0);
		remaining-=c.getLitersWanted(); //processing this customer, hence update local gas amount indicator accordingly
		return c;
	}

	
	
}
