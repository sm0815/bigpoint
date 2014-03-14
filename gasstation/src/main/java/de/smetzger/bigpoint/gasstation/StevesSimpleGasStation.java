package de.smetzger.bigpoint.gasstation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

public class StevesSimpleGasStation implements GasStation{
	
	//the pumps
	protected List<GasPump> pumps=new ArrayList<GasPump>();
	//and their attendants (each attendant is responsible for a single pump)
	protected List<PumpAttendant> attendants=new ArrayList<PumpAttendant>();
	
	//statistics attributes
	protected volatile double revenue=0;
	protected AtomicInteger cancelledTooExpensive=new AtomicInteger();
	protected AtomicInteger cancelledAllOutaGas=new AtomicInteger();
	protected AtomicInteger sold=new AtomicInteger();
	//the prices
	protected double[] prices=new double[GasType.values().length];
	
	//how long to sleep before trying again to match an attendant to the current request
	protected final static int sleepTime=100;
	
	/** constructor */
	public StevesSimpleGasStation(){
		for(int i=0;i<prices.length;i++)
			prices[i]=0;
	}
	

	/**
	 * Add a gas pump to this station.
	 * This is used to set up this station.
	 * Assigns the pump to an attendant.
	 * 
	 * @param pump
	 *            the gas pump
	 */
	public void addGasPump(GasPump pump) {
		pumps.add(pump);	
		attendants.add(new PumpAttendant(pump));
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
	 *             thrown in case not enough gas of this type can be provided
	 *             by any single {@link GasPump}.
	 * @throws GasTooExpensiveException
	 *             thrown if gas is not sold at the requested price (or any lower price)
	 */
	public double buyGas(GasType type, double amountInLiters,
			double maxPricePerLiter) throws NotEnoughGasException,
			GasTooExpensiveException {
		
		PumpAttendant attendant=null;
		
		// we continuously try to find an attendant that can handle the current request
		// until one is found or none can possibly handle the request 
		while((attendant=acquireMatchingPumpAttendant(type,amountInLiters))==null){				
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				// ignore
			}			
		}
		
		/* retrieves the price (which has been fixed and 
		 * remembered when the attendant was acquired), 
		 * then starts the pumping if agreeable to customer; 
		 */
		double price=attendant.getAgreedPrice();
		
		if(maxPricePerLiter<price){ //check that price is agreeable
			cancelledTooExpensive.incrementAndGet();
			freeAttendant(attendant);
			throw new GasTooExpensiveException();
		}		
		
		attendant.pumpGas(amountInLiters);		
		sold.incrementAndGet();	
		
		freeAttendant(attendant);
		
		double cost=price*amountInLiters;
		addRevenue(cost);
		
		return cost;
	}
	
	/** adds the cost of a gas purchase to the overall revenue */
	synchronized protected void addRevenue(double purchaseCost){
		revenue+=purchaseCost;
	}

	/** tries to acquire an attendant responsible for a pump 
	 *  that has enough gas for the given requirements 
	 *  @return an attendant that can satisfy the customers needs 
	 *           or null if all matching attendants are busy handling other customers
	 *  @throws NotEnoughGasException - if no pump available with enough gas 
	 *  @Note: As GasPump is not thread-safe (meaning we cannot always 
	 *         be sure about the 'remaining' gas value), 
	 *         this implementation may wait longer than strictly necessary 
	 *         before it throws a NotEnoughGasException;
	 *         still it will eventually throw the exception, 
	 *         but may wait in some cases until all previous customers 
	 *         (for the same gas type) are dealt with */
	synchronized  protected PumpAttendant acquireMatchingPumpAttendant(GasType type, double amountInLiters) 
			throws NotEnoughGasException{
		//flag that indicates that there is a potentially matching attendant 
		//that is currently busy with another customer request
		boolean possibleMatchBusy=false; 
		
		for(PumpAttendant attendant:attendants){ //simply check all attendants if they match gas type and have enough gas left
			if(attendant.getGasType()==type && attendant.getRemainingAmount()>=amountInLiters){				
				if(attendant.isBusy())  //oh, a match, but he is busy...
					possibleMatchBusy=true;
				else{ //oh a match, and available, take it 
					attendant.reserveForCustomer(amountInLiters,prices[type.ordinal()]);
					return attendant;
				}				
			}
		}
		if(!possibleMatchBusy){ //no match, not even one that is busy, well than we do not have what the customer needs
			cancelledAllOutaGas.incrementAndGet();
			throw new NotEnoughGasException();
		}			
		else 
			return null;
	}
	
	/** frees up an attendant after he served a customer 
	 *  (or after the customer aborted the service since the gas was too expensive) 
	 */
	synchronized protected void freeAttendant(PumpAttendant attendant){
		attendant.setBusy(false);
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

	
	// just a main to try it all out
	public static void main(String [] args)
	{
		GasStation station=new StevesSimpleGasStation();
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
