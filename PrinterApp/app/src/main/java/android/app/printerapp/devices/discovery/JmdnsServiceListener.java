package android.app.printerapp.devices.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import android.app.printerapp.devices.DevicesFragment;
import android.app.printerapp.devices.DevicesListController;
import android.app.printerapp.model.ModelPrinter;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;


/**
 * Service listener class that will handle new device discovery and add them 
 * to a permanent list
 * @author alberto-baeza
 *
 */
public class JmdnsServiceListener implements ServiceListener{
	
	//Allows this application to receive Multicast packets, can cause a noticable battery drain.
		private static WifiManager.MulticastLock mMulticastLock;
				
		//New thread to handle both listeners.
		private Handler mHandler;
			
		//JmDNS manager which will create both listeners
		private static JmDNS mJmdns = null;
		
		//Reference to the fragment controller
		private DevicesFragment mContext;
		
		/*****************
		 * Main constructor
		 * @param context
		 *****************/
		
		public JmdnsServiceListener(DevicesFragment context){
			
			mContext = context;
						
			//Create new Handler after 1s to setup the service listener
			//TODO: Check if it's actually listening because it seems unreliable
			mHandler = new Handler();		
			mHandler.postDelayed(new Runnable() {
				
	            public void run() {
	                setUp();
	            }
	        }, 1000);
			
			
	
		}
		
		/**
		 * Setup our Listener to browse services on the local network
		 */
		
		private void setUp() {
			
			try{
			//We need to get our device IP address to bind it when creating JmDNS since we want to address it to a specific network interface
	        WifiManager wifi = (WifiManager) mContext.getActivity().getSystemService(Context.WIFI_SERVICE);
	        final InetAddress deviceIpAddress = getDeviceIpAddress(wifi);
	        
	        //Get multicast lock because we need to listen to multicast packages
	        mMulticastLock = wifi.createMulticastLock(getClass().getName());
	        mMulticastLock.setReferenceCounted(true);
	        mMulticastLock.acquire();
	        
	        Log.i("Model", "Starting JmDNS Service Listener...." + deviceIpAddress.toString());
	
				mJmdns = JmDNS.create(deviceIpAddress, null); //Creating an instance of JmDNS			
				//Search for an specific service type
	            mJmdns.addServiceListener("_ipp3._tcp.local.", this);
	            
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException n){
				n.printStackTrace();
			}

	           
	    }

		/****************************************
		 * Handlers for ServiceListener
		 ****************************************/
		
		@Override
		public void serviceAdded(ServiceEvent event) {
					
			//When a service is found, we request it to be resolved
			mJmdns.requestServiceInfo(event.getType(), event.getName(), 1);
			
		}

		@Override
		public void serviceRemoved(ServiceEvent event) {
				 
		}

		@Override
		public void serviceResolved(ServiceEvent event) {	
			
			//Creates a service with info
			 Log.i("Model","Service resolved: " + event.getInfo().getQualifiedName() + " port:" + event.getInfo().getPort());
			 ServiceInfo service = mJmdns.getServiceInfo(event.getType(), event.getName());
			
			 mContext.addElement(new ModelPrinter(service.getName(),
					 service.getInetAddresses()[0].toString(), searchPrinter(DevicesListController.getNetworkId(event.getName()))));
			 
		}
		
	
		
		//This method was obtained externally, basically it gets our IP Address, or return Android localhost by default.
		public  static InetAddress getDeviceIpAddress(WifiManager wifi) {
			
			   InetAddress result = null;
			   try {
			      //Default to Android localhost
			      result = InetAddress.getByName("10.0.0.2");
			      
			      //Figure out our wifi address, otherwise bail
			      WifiInfo wifiinfo = wifi.getConnectionInfo();
			      
			      int intaddr = wifiinfo.getIpAddress();
			      byte[] byteaddr = new byte[] { (byte) (intaddr & 0xff), (byte) (intaddr >> 8 & 0xff), (byte) (intaddr >> 16 & 0xff), (byte) (intaddr >> 24 & 0xff) };
			      result = InetAddress.getByAddress(byteaddr);
			      
			   } catch (UnknownHostException ex) {
			      Log.i("Controller", String.format("getDeviceIpAddress Error: %s", ex.getMessage()));
			   }
			   return result;
		}


    /**
     * Reload the service discovery by registering the service again in case it's not detected automatically
     */
		public void reloadListening(){

			mJmdns.unregisterAllServices();
			mMulticastLock.release();

            mMulticastLock.acquire();
            //Search for an specific service type
            mJmdns.addServiceListener("_ipp3._tcp.local.", this);
			
			
		}
		
		//Search the printer by the network id and take its position		
		public int searchPrinter(String name){
			
			int pos = DevicesListController.searchAvailablePosition();
			
			Log.i("SEARCH","Searching for... " + name);
			
			if ((name.length()==4)&&(name!=null))
			for (ModelPrinter p : DevicesListController.getList()){
				
				if (p.getName().contains(name)){
					
					Log.i("OUT","Match found!: " + p.getName());
					pos = p.getPosition();
					DevicesListController.getList().remove(p);
				} else Log.i("OUT","Not found!: " + p.getName());
				
			}
			
			return pos;
			
		}
		
	}
