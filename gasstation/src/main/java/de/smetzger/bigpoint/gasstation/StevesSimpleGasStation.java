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
	
	protected List<GasPump> pumps=new ArrayList<GasPump>();
	protected List<PumpAttendant> attendants=new ArrayList<PumpAttendant>();
	
	
	protected volatile double revenue=0;
	protected AtomicInteger cancelledTooExpensive=new AtomicInteger();
	protected AtomicInteger cancelledAllOutaGas=new AtomicInteger();
	protected AtomicInteger sold=new AtomicInteger();
	protected double[] prices=new double[GasType.values().length];
	
	protected final static int sleepTime=100;
	
	public StevesSimpleGasStation(){
		for(int i=0;i<prices.length;i++)
			prices[i]=0;
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
	 *             Should be thrown in case not enough gas of this type can be provided
	 *             by any single {@link GasPump}.
	 * @throws GasTooExpensiveException
	 *             Should be thrown if gas is not sold at the requested price (or any lower price)
	 */
	public double buyGas(GasType type, double amountInLiters,
			double maxPricePerLiter) throws NotEnoughGasException,
			GasTooExpensiveException {
		
		PumpAttendant attendant=null;
		
		while((attendant=acquireMatchingPumpAttendant(type,amountInLiters))==null){				
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		
		/* retrieves the price, then starts the pumping if agreeable to customer; 
		 * the price is fixed when the PumpAttendant/GasPump is assigned,
		 * so if we need to be really strict about concurrent price changes
		 * the setPrice method could by synchronized as well 
		 * (at the moment the pump assignment could have started but still be 
		 *  affected by a price change coming in after it started; 
		 *  still, selling to a price not accepted by the customer is 
		 *  prevented by fixing the price based on a single read operation;
		 *  this price then is considered the "agreed price" for this transaction
		 *  i.e. price changes after this point do not affect the ongoing transaction
		 */
		double price=attendant.getAgreedPrice();
		
		if(maxPricePerLiter<price){
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

	/** tries to acquire a pump that has enough gas for the given requirements, 
	 *  @return a pump that can satisfy the customers needs 
	 *           or null if all matching pumps are busy handling other customers
	 *  @throws NotEnoughGasException - if no pump available with enough gas 
	 *  @Note: As GasPump is not thread-safe, this implementation may wait longer than 
	 *         strictly necessary before it throws a NotEnoughGasException;
	 *         still it will eventually throw the error, 
	 *         but may wait in some cases until all previous customers 
	 *         (for the same gas type) are dealt with */
	synchronized  protected PumpAttendant acquireMatchingPumpAttendant(GasType type, double amountInLiters) 
			throws NotEnoughGasException{
		boolean possibleMatchBusy=false;
		System.out.println("Checking for "+type+"/"+amountInLiters);
		for(PumpAttendant attendant:attendants){
			System.out.println("attendant: "+attendant.getGasType()+"/"+attendant.getRemainingAmount());
			if(attendant.getGasType()==type && attendant.getRemainingAmount()>=amountInLiters){				
				if(attendant.isBusy())
					possibleMatchBusy=true;
				else{
					attendant.reserveForCustomer(amountInLiters,prices[type.ordinal()]);
					return attendant;
				}				
			}
		}
		if(!possibleMatchBusy){
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
