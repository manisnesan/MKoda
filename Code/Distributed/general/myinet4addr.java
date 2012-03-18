package general;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class myinet4addr {

	public InetAddress my4Ia;
	
	public InetAddress getMy4Ia() {
		return my4Ia;
	}

	public myinet4addr() throws Exception {
		Enumeration<NetworkInterface> e1 = (Enumeration<NetworkInterface>)NetworkInterface.getNetworkInterfaces();
		while(e1.hasMoreElements()) {
			NetworkInterface ni = e1.nextElement();
			if(ni.isLoopback()) { continue; }

			Enumeration<InetAddress> e2 = ni.getInetAddresses();

			while(e2.hasMoreElements()) {
				Inet4Address ia4;
				InetAddress ia = e2.nextElement();
				try{
					ia4 = (Inet4Address) ia;
				}
				catch (Exception e){
					continue;
				}
				this.my4Ia=ia4;
			}
		}

	}

	public static void main(String[] args) throws Exception {

	}

}
