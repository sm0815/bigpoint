package de.smetzger.bigpoint.gasstation;



import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasType;

/** encapsulates GasPump,
 * provides an indicator whether GasPump in use and
 * rememebrs the price for the user currently served.  
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
	}

}
