/* MKoda 
 * Peer to Peer File Sharing System with a search engine
 * Please follow the readme file for instructions on how to setup and run the system 
 * Written by : Arun Natarajan, Manikandan Sivanesan, Koushik Krishnakumar, Dinesh Radha Kirshan
*/
package client;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class logMessages{
	JFrame frame;
	JPanel panel;
	JTextArea area;
	JScrollPane spane;
	public static void main(String[] args) {
		//logMessages v = new logMessages();
	}

	public logMessages(){
		frame = new JFrame("Log Messages");
		panel = new JPanel();
		area = new JTextArea( "", 12, 42);
		area.setEditable(false);
		spane = new JScrollPane(area);
		panel.add(spane);
		frame.add(panel);
		frame.setSize(500, 230);
		//frame.setResizable(false);
		frame.setVisible(false);
	}
}