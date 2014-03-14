package de.smetzger.bigpoint.gasstation.greedy;



import java.util.ArrayList;
import java.util.List;

import de.smetzger.bigpoint.gasstation.greedy.Customer.State;

import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasType;

/** encapsulates GasPump,
 * provides an indicator whether GasPump in use and
 * allows to update estimated remaining gas amount 
 * independent of the pumping process, thus allowing 
 * to keep the current gas level accessible in a 
 * thread-safe way.  
 */
public class QueueingPumpAttendant implements Runnable{
	
	protected GasPump pump=null;
	protected StevesGreedyGasStation station=null;
	//AtomicBoolean busy=new AtomicBoolean(false);
	
	private double remaining;
	private double remainingAmountAfterQueueProcessing;
	

	private List<Customer> queuedCustomers=new ArrayList<Customer>();
	
	public QueueingPumpAttendant (StevesGreedyGasStation station, GasPump pump){
		this.pump=pump;
		this.remaining=pump.getRemainingAmount();
		remainingAmountAfterQueueProcessing=remaining;
		this.station=station;
	}
	

	synchronized public boolean tryToQueueCustomer(Customer c){
		System.out.println("Trying to queue "+c);
		System.out.println("remaining: "+remainingAmountAfterQueueProcessing+"/"+remaining );
		
		if(c.getGasType()!=getGasType())
			return false;
		if(getRemainingAmountAfterQueueProcessing()<c.getLitersWanted())
			return false;
		queuedCustomers.add(c);
		remainingAmountAfterQueueProcessing-=c.getLitersWanted();
		return true;
	}
	
	
	public double getRemainingAmount() {
		return remaining;
	}

	
	public double getRemainingAmountAfterQueueProcessing() {
		
/*		double remainingAmountAfterQueueProcessing=remaining;
		for(Customer c:queuedCustomers)
			remainingAmountAfterQueueProcessing-=c.getLitersWanted(); */
		return remainingAmountAfterQueueProcessing;
	}
	
	

	public GasType getGasType() {
		return pump.getGasType();
	}

	/*public void queueCustomer(Customer c){
		queuedCustomers.add(c);
	} */
	
	synchronized public List<Customer> emptyCustomerQueue(){
		List<Customer> customersQueued=queuedCustomers;
		queuedCustomers=new ArrayList<Customer>(GasType.values().length);
		remainingAmountAfterQueueProcessing=remaining;
		return customersQueued;
	}
	
	public void pumpGas(double amount) {
		System.out.println("pumping "+amount+"l");
		pump.pumpGas(amount);
	}
	
	synchronized protected boolean noCustomers(){
		return queuedCustomers.isEmpty();
	}

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
	
	protected void processNextCustomer(){
		Customer customer=pickCustomer();
		pumpGas(customer.getLitersWanted());
		customer.setState(State.Served);
		notifyGasStation();		
	}
	
	synchronized protected void notifyGasStation(){
		synchronized(station){
		station.notifyAll();
		}
	}
	

	synchronized protected Customer pickCustomer(){	
		Customer c=queuedCustomers.get(0);
		queuedCustomers.remove(0);
		remaining-=c.getLitersWanted();
		return c;
	}

	
	
}
