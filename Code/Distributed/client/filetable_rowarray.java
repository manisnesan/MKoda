/* MKoda 
 * Peer to Peer File Sharing System with a search engine
 * Please follow the readme file for instructions on how to setup and run the system 
 * Written by : Arun Natarajan, Manikandan Sivanesan, Koushik Krishnakumar, Dinesh Radha Kirshan
*/
package client;

import java.net.InetAddress;
import general.*;

public class filetable_rowarray {
	
	public byte IP[]=new byte[4];
	public byte[] user=null;
	public byte port_no[]=new byte[2];
	public byte[] filename=null;
	public byte[] filesize=new byte[2];
	public byte[] filedigest=new byte[1024];
	public byte[] msgdigest=new byte[20];
	public byte[] downloads=new byte[2];
	
	public byte[] row_bytearray;
	public genfunc gfns = new genfunc();
	
	
	public filetable_rowarray(InetAddress ip, String user,int port_no, String filename, int filesize, byte[] filedigest, byte[] msgdigest, int downloads) {
		super();
		this.IP = ip.getAddress();
		this.user = new byte[user.length()];
		this.user=user.getBytes();
		
		this.port_no = gfns.convIntBary_2(port_no);
		
		this.filename=new byte[filename.length()];
		this.filename = filename.getBytes();
		
		this.filesize = gfns.convIntBary(filesize);
		this.filedigest = filedigest;
		this.msgdigest = msgdigest;
		this.downloads = gfns.convIntBary(downloads);
	}
	
	public filetable_rowarray() {
		
	}

	public byte[] getRow(){
		
		row_bytearray=new byte[this.IP.length + 2+ this.user.length + this.port_no.length + 2 +this.filename.length + this.filesize.length + this.filedigest.length + this.msgdigest.length + this.downloads.length];
		int index=0;
		
		System.arraycopy(this.IP, 0, row_bytearray, index,4);
		index+=4;
		
		System.arraycopy(gfns.convIntBary_2(this.user.length), 0, row_bytearray, index,2);
		index+=2;
		
		System.arraycopy(this.user, 0, row_bytearray, index,this.user.length);
		index+=this.user.length;
		
		System.arraycopy(this.port_no, 0, row_bytearray, index,2);
		index+=2;
		
		System.arraycopy(gfns.convIntBary_2(this.filename.length), 0, row_bytearray, index,2);
		index+=2;
		
		System.arraycopy(this.filename, 0, row_bytearray, index,this.filename.length);
		index+=this.filename.length;
		
		System.arraycopy(this.filesize, 0, row_bytearray, index,2);
		index+=2;
		
		System.arraycopy(this.filedigest, 0, row_bytearray, index,1024);
		index+=1024;
		
		System.arraycopy(this.msgdigest, 0, row_bytearray, index,20);
		index+=20;
		
		System.arraycopy(this.downloads, 0, row_bytearray, index,2);
		index+=2;
					
		return(row_bytearray);
	}
	
	public void setRow(byte[] row_bytearray){
		
		int index=0;
		byte[] lengthF=new byte[2];
		
		System.arraycopy(row_bytearray,index,this.IP,0,4);
		index+=4;
		
		System.arraycopy(row_bytearray,index,lengthF,0,2);
		index+=2;
		this.user=new byte[gfns.convBaryInt(lengthF)];
		System.arraycopy(row_bytearray,index,this.user,0,gfns.convBaryInt(lengthF));
		index+=gfns.convBaryInt(lengthF);
		
		System.arraycopy(row_bytearray,index,this.port_no,0,2);
		index+=2;
		
		System.arraycopy(row_bytearray,index,lengthF,0,2);
		index+=2;
		this.filename=new byte[gfns.convBaryInt(lengthF)];
		System.arraycopy(row_bytearray,index,this.filename,0,gfns.convBaryInt(lengthF));
		index+=gfns.convBaryInt(lengthF);
		
		System.arraycopy(row_bytearray,index,this.filesize,0,2);
		index+=2;
		
		System.arraycopy(row_bytearray,index,this.filedigest,0,1024);
		index+=1024;
		
		System.arraycopy(row_bytearray,index,this.msgdigest,0,20);
		index+=20;
		
		System.arraycopy(row_bytearray,index,this.downloads,0,2);
		index+=2;

	}
	
	public int getDownloads() {
		return gfns.convBaryInt(downloads);
	}


	public String getFiledigest() {
		return gfns.ByteArraytohexString(filedigest);
	}


	public String getFilename() {
		return (new String(filename));
	}


	public int getFilesize() {
		return gfns.convBaryInt(filesize);
	}



	public InetAddress getIP() throws Exception  {
		return InetAddress.getByAddress(IP);
	}


	public String getMsgdigest() {
		return gfns.ByteArraytohexString(msgdigest);
	}


	public int getPort_no() {
		return gfns.convBaryInt(port_no);
	}


	public String getUser() {
		return (new String(user));
	}

	public byte[] getRow_bytearray() {
		return row_bytearray;
	}
	
			
}
