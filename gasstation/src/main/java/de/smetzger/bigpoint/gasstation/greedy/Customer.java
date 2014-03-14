package de.smetzger.bigpoint.gasstation.greedy;

import net.bigpoint.assessment.gasstation.GasType;


/** models a customer (request), 
 *  basically aggregating the request components in an object 
 *  (plus the state of the request processing) */
public class Customer {
	// models the state a customer might be in 
	// (either waiting, already completely served or 
	//  determined to have a request that cannot be met)
	public enum State {InProcess, Served, CannotBeServed}
	protected volatile State state=State.InProcess;
	
	protected double litersWanted;
	protected double maxPricePaid;
	protected GasType gasType;
	
	
	public Customer(GasType gastype, double liters, double price){
		litersWanted=liters;
		maxPricePaid=price;
		gasType=gastype;
	}

	public double getLitersWanted() {
		return litersWanted;
	}

	public double getMaxPricePaid() {
		return maxPricePaid;
	}
	
	

	public GasType getGasType() {
		return gasType;
	}

	public State getState() {
		return state;
	}	

	public void setState(State newState) {
		state=newState;
	}
	
	@Override
	public String toString(){
		return "[gas: "+gasType+", liters: "+litersWanted+", price: "+maxPricePaid+"]";
	}
	
	

}
