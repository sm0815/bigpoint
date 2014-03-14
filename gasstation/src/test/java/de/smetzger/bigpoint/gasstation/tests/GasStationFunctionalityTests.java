package de.smetzger.bigpoint.gasstation.tests;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;
import de.smetzger.bigpoint.gasstation.StevesSimpleGasStation;
import de.smetzger.bigpoint.gasstation.greedy.StevesGreedyGasStation;
import de.smetzger.bigpoint.gasstation.tests.Client.DelayedCustomer;


public class GasStationFunctionalityTests {

	protected GasStation generateStation(){
		GasStation station=new StevesSimpleGasStation();		
		station.addGasPump(new GasPump(GasType.DIESEL, 15));
		station.addGasPump(new GasPump(GasType.REGULAR, 150));
		station.addGasPump(new GasPump(GasType.SUPER, 15));
		station.addGasPump(new GasPump(GasType.SUPER, 10));
		
		station.setPrice(GasType.DIESEL, 2);
		station.setPrice(GasType.SUPER, 2);
		station.setPrice(GasType.REGULAR, 2);
		
		return station;
	}
	
	
	@Ignore
	@Test
	public void singleThreadTest1(){
		
		GasStation station=generateStation();	
	
		try {
			station.buyGas(GasType.DIESEL, 80, 2);
		} catch (NotEnoughGasException | GasTooExpensiveException e) {
			//Ignore
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 1);
		} catch (NotEnoughGasException | GasTooExpensiveException e) {
			//Ignore
		}
		
		try {
			station.buyGas(GasType.DIESEL, 5, 2);
		} catch (NotEnoughGasException | GasTooExpensiveException e) {
			//Ignore
		}
		
		try {
			station.buyGas(GasType.DIESEL, 4, 2);
		} catch (NotEnoughGasException | GasTooExpensiveException e) {
			//Ignore
		}
		
		try {
			station.buyGas(GasType.DIESEL, 5, 2);
		} catch (NotEnoughGasException | GasTooExpensiveException e) {
			//Ignore
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 2);
		} catch (NotEnoughGasException | GasTooExpensiveException e) {
			//Ignore
		}
		
		try {
			station.buyGas(GasType.DIESEL, 3, 1);
		} catch (NotEnoughGasException | GasTooExpensiveException e) {
			//Ignore
		}
		
		assertEquals("3 sold", 3, station.getNumberOfSales());
		assertEquals("3 outagas", 3, station.getNumberOfCancellationsNoGas());
		assertEquals("1 too expensive", 1, station.getNumberOfCancellationsTooExpensive());
		assertEquals("28 revenue", 28d, station.getRevenue(), 0.001);	
		/*
		System.out.println("Sold: "+station.getNumberOfSales());
		System.out.println("OutaGas: "+station.getNumberOfCancellationsNoGas());
		System.out.println("Expensive: "+station.getNumberOfCancellationsTooExpensive());
		System.out.println("Revenue: "+station.getRevenue());
		*/
	}
	
	
	@Ignore
	@Test
	public void testMultiThreadBasicRacing(){
		GasStation station=generateStation();	
		Client c1=new Client(station,8,GasType.DIESEL,5);
		Client c2=new Client(station,10,GasType.DIESEL,5);
		
		Thread t1=new Thread(c1);
		Thread t2=new Thread(c2);
		t1.start();		
		t2.start();
		
		while(t1.isAlive() || t2.isAlive())
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		assertEquals("1 sold", 1, station.getNumberOfSales());
		assertEquals("1 outagas", 1, station.getNumberOfCancellationsNoGas());
		assertEquals("0 too expensive", 0, station.getNumberOfCancellationsTooExpensive());
		assertTrue("20 or 16 revenue", (20d== station.getRevenue() || 16d==station.getRevenue()));	
/*		
		System.out.println("Sold: "+station.getNumberOfSales());
		System.out.println("OutaGas: "+station.getNumberOfCancellationsNoGas());
		System.out.println("Expensive: "+station.getNumberOfCancellationsTooExpensive());
		System.out.println("Revenue: "+station.getRevenue());
		*/	
	}
	
	
//	@Ignore
	@Test
	public void testMultiThreadParallelUsage(){
		GasStation station=generateStation();	
		Client c1=new Client(station,4,GasType.SUPER,5);
		Client c2=new DelayedCustomer(station,6,GasType.SUPER,5,100);
		Client c3=new DelayedCustomer(station,5,GasType.SUPER,5,100);
		Client c4=new DelayedCustomer(station,5,GasType.SUPER,5,100);
		Client c5=new DelayedCustomer(station,5,GasType.SUPER,5,100);
		
		Thread t1=new Thread(c1);
		Thread t2=new Thread(c2);
		Thread t3=new Thread(c3);
		Thread t4=new Thread(c4);
		Thread t5=new Thread(c5);
		t1.start();		
		t2.start();
		t3.start();
		t4.start();
		t5.start();
		
		while(t1.isAlive() || t2.isAlive()|| t3.isAlive()|| t4.isAlive()|| t5.isAlive())
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		/*
		assertEquals("5 sold", 5, station.getNumberOfSales());
		assertEquals("0 outagas", 0, station.getNumberOfCancellationsNoGas());
		assertEquals("0 too expensive", 0, station.getNumberOfCancellationsTooExpensive());
		assertTrue("50 revenue", (50d== station.getRevenue() || 18d==station.getRevenue()));	
		*/
		System.out.println("Sold: "+station.getNumberOfSales());
		System.out.println("OutaGas: "+station.getNumberOfCancellationsNoGas());
		System.out.println("Expensive: "+station.getNumberOfCancellationsTooExpensive());
		System.out.println("Revenue: "+station.getRevenue());
		
			
	}

	
	@Ignore
	@Test
	public void testMultiThreadQueuedRacing(){
		GasStation station=generateStation();	
		Client c1=new Client(station,4,GasType.DIESEL,5);
		Client c2=new DelayedCustomer(station,5,GasType.DIESEL,5,100);
		Client c3=new DelayedCustomer(station,11,GasType.DIESEL,5,100);
		
		Thread t1=new Thread(c1);
		Thread t2=new Thread(c2);
		Thread t3=new Thread(c3);
		t1.start();		
		t2.start();
		t3.start();
		
		while(t1.isAlive() || t2.isAlive()|| t3.isAlive())
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		assertEquals("2 sold", 2, station.getNumberOfSales());
		assertEquals("1 outagas", 1, station.getNumberOfCancellationsNoGas());
		assertEquals("0 too expensive", 0, station.getNumberOfCancellationsTooExpensive());
		assertTrue("30 or 18 revenue", (30d== station.getRevenue() || 18d==station.getRevenue()));	
/*		
		System.out.println("Sold: "+station.getNumberOfSales());
		System.out.println("OutaGas: "+station.getNumberOfCancellationsNoGas());
		System.out.println("Expensive: "+station.getNumberOfCancellationsTooExpensive());
		System.out.println("Revenue: "+station.getRevenue());
		
			*/
	}
	
	@Ignore
	@Test
	public void testMultiThreadQueuedRacingOptimal(){
		GasStation station=generateStation();	
		Client c1=new Client(station,4,GasType.DIESEL,5);
		Client c2=new DelayedCustomer(station,5,GasType.DIESEL,5,100);
		Client c3=new DelayedCustomer(station,11,GasType.DIESEL,5,100);
		
		Thread t1=new Thread(c1);
		Thread t2=new Thread(c2);
		Thread t3=new Thread(c3);
		t1.start();		
		t2.start();
		t3.start();
		
		while(t1.isAlive() || t2.isAlive()|| t3.isAlive())
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		assertEquals("2 sold", 2, station.getNumberOfSales());
		assertEquals("1 outagas", 1, station.getNumberOfCancellationsNoGas());
		assertEquals("0 too expensive", 0, station.getNumberOfCancellationsTooExpensive());
		assertEquals("30 revenue", 30d, station.getRevenue(),0.00001);	
/*		
		System.out.println("Sold: "+station.getNumberOfSales());
		System.out.println("OutaGas: "+station.getNumberOfCancellationsNoGas());
		System.out.println("Expensive: "+station.getNumberOfCancellationsTooExpensive());
		System.out.println("Revenue: "+station.getRevenue());
		*/	
	}
	
	@Ignore
	@Test
	public void testPriceChanges(){
		GasStation station=generateStation();	
		Client c1=new Client(station,4,GasType.DIESEL,5);
		Client c2=new DelayedCustomer(station,5,GasType.DIESEL,5,100);
		Client c3=new DelayedCustomer(station,3,GasType.DIESEL,5,100);
		
		Thread t1=new Thread(c1);
		Thread t2=new Thread(c2);
		Thread t3=new Thread(c3);
		t1.start();
		try {
			Thread.sleep(10);
		} catch (InterruptedException e1) {
			// Ignore
		}
		station.setPrice(GasType.DIESEL, 5);
		t2.start();
		t3.start();
		
		
		while(t1.isAlive() || t2.isAlive()|| t3.isAlive())
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		assertEquals("3 sold", 3, station.getNumberOfSales());
		assertEquals("0 outagas", 0, station.getNumberOfCancellationsNoGas());
		assertEquals("0 too expensive", 0, station.getNumberOfCancellationsTooExpensive());
		assertEquals("48 revenue", 48d, station.getRevenue(),0.00001);	
/*		
		System.out.println("Sold: "+station.getNumberOfSales());
		System.out.println("OutaGas: "+station.getNumberOfCancellationsNoGas());
		System.out.println("Expensive: "+station.getNumberOfCancellationsTooExpensive());
		System.out.println("Revenue: "+station.getRevenue());
		*/	
	}
	
	
}
