package de.smetzger.bigpoint.gasstation.tests;

import java.util.Random;

import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

public class Client implements Runnable {
	protected double litersWanted;
	protected GasType gasTypeWanted;
	protected double maxPrizePaid;
	protected GasStation station;
	protected double paid=0;
	protected Exception ex=null;
	
	public Client(GasStation station, double liters, GasType type, double maxPrize) {
		litersWanted=liters;
		gasTypeWanted=type;
		maxPrizePaid=maxPrize;
		this.station=station;
	}
	
	public void run() {
		try {
			paid=station.buyGas(gasTypeWanted, litersWanted, maxPrizePaid);
		} catch (NotEnoughGasException | GasTooExpensiveException e) {
			ex=e;
			paid=0;
		}
	}

	public double getPaid() {
		return paid;
	}


	public Exception getEx() {
		return ex;
	}

	public static class DelayedCustomer extends Client{
		
		protected int delay=0;

		public DelayedCustomer(GasStation station, double liters,
				GasType type, double maxPrize, int delay) {
			super(station, liters, type, maxPrize);			
			this.delay=delay;
		}
		
		public void run() {
			
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			try {				
				paid=station.buyGas(gasTypeWanted, litersWanted, maxPrizePaid);
			} catch (NotEnoughGasException | GasTooExpensiveException e) {
				ex=e;
				paid=0;
			}
		}
		
	}
	
	public static class RandomizedCustomer extends DelayedCustomer{		
		protected static final Random rand = new Random();

		public RandomizedCustomer(GasStation station, double liters,
				GasType type, double maxPrize) {
			super(station, liters, type, maxPrize,rand.nextInt(2000));			
		}
		
	}
	
	
}
