package de.smetzger.bigpoint.gasstation.greedy;

import net.bigpoint.assessment.gasstation.GasType;



public class Customer {
	public enum State {InProcess, Served, CannotBeServed}
	protected double litersWanted;
	protected double maxPricePaid;
	protected GasType gasType;
	

	protected volatile State state=State.InProcess;
	
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
