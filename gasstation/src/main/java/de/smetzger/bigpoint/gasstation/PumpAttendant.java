package de.smetzger.bigpoint.gasstation;



import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasType;

/** encapsulates GasPump,
 * provides an indicator whether GasPump in use and
 * allows to update estimated remaining gas amount 
 * independent of the pumping process, thus allowing 
 * to keep the current gas level accessible in a 
 * thread-safe way.  
 */
public class PumpAttendant {
	
	protected GasPump pump=null;
	protected boolean busy=false;	
	private double pricePerLiterForCurrentCustomer;
	
	public PumpAttendant (GasPump pump){
		this.pump=pump;	
	}
	
	public boolean isBusy(){
		return busy;
	}
	
	public void setBusy(boolean value){
		busy=value;
	}
	
	
	public void reserveForCustomer(double litersToBeTaken, double currentPrice){
		busy=true;		
		pricePerLiterForCurrentCustomer=currentPrice;
	}
	
	public double getAgreedPrice(){
		return pricePerLiterForCurrentCustomer;
	}
	
	public double getRemainingAmount() {
		return pump.getRemainingAmount();		
	}
	
	

	public GasType getGasType() {
		return pump.getGasType();
	}

	
	
	public void pumpGas(double amount) { 
		pump.pumpGas(amount);
		// a possible extension of the model could consider a customer
		// changing his mind about the amount of gas he takes
		// which might be modelled via an exception and/or by syncing the 
		// PumpAttendants estimation of remaining gas level with the actual gas level
		//remaining=pump.getRemainingAmount();			
	}

}
