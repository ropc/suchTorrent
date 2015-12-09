/**
 * Written by John Jordan, Rodrigo Pacheco Curro, and Robert Sehringer
 */
import java.net.*;
import java.util.*;
import java.nio.*;
import java.nio.file.Paths;

import GivenTools.*;
import java.util.concurrent.*;
import java.awt.event.*;
import java.io.File;
import java.awt.TextField;
import javax.swing.*;
import java.awt.Color;
import java.lang.Integer;
import java.awt.BorderLayout;
import javax.swing.GroupLayout.Alignment;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.RowSpec;
import com.jgoodies.forms.layout.FormSpecs;

public class RUBTClient {

	public static final String peerId = generatePeerId();
	private static int port;
	/**
	 * returns a randomly generated peer id to be used for
	 * communication with peers/tracker
	 * @return the peer id
	 */
	public static String generatePeerId() {
		char[] chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
		Random rando = new Random();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			sb.append(chars[rando.nextInt(chars.length)]);
		}
		return sb.toString();	
	}

	public static int getListenPort(){
		return port;
	}

	
	
	private static JTextField textField;
	private static JTextField textField_1;

	/**
	 * main method for BitTorrent client.
	 * creates a TorrentHandler object that will handle the download
	 * for a given torrent.
	 * @param args command line arguments
	 *             these should be torrentFileName saveFileName
	 */
	public static void main(String[] args){
		k();
	}
	
	public static void k(){
		JFrame window = new JFrame();
		window.setTitle("RUBT Client");
		window.getContentPane().setLayout(null);
		window.setSize(420, 320);
		textField = new JTextField();
		textField.setBounds(242, 77, 152, 20);
		window.getContentPane().add(textField);
		textField.setColumns(10);
		
		textField_1 = new JTextField();
		textField_1.setBounds(242, 108, 152, 20);
		window.getContentPane().add(textField_1);
		textField_1.setColumns(10);
		
		JLabel lblNameOfTorrent = new JLabel("Name of Torrent File:");
		lblNameOfTorrent.setBounds(10, 79, 117, 20);
		window.getContentPane().add(lblNameOfTorrent);
		
		JLabel lblNameOfOutput = new JLabel("Name of Output File:");
		lblNameOfOutput.setBounds(10, 111, 117, 24);
		window.getContentPane().add(lblNameOfOutput);
		
		JButton btnDownload = new JButton("Download");
		btnDownload.setBounds(140, 206, 100, 23);
		window.getContentPane().add(btnDownload);
		
		JPanel window3 = new JPanel();
		window3.setBounds(0, 0, 404, 282);
		window.getContentPane().add(window3);
		btnDownload.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				File Torrent = Paths.get(textField.getText()).toFile();
				File Output = Paths.get(textField_1.getText()).toFile();
				if(!Torrent.exists()){JOptionPane.showMessageDialog(window, "Torrent File Does Not Exist","Torrent Read Error",JOptionPane.ERROR_MESSAGE);return;}
				if(Output.exists()){JOptionPane.showMessageDialog(window, "Output File Already Exists","Continue At Your Own Risk",JOptionPane.WARNING_MESSAGE);}
				btnDownload.setBounds(130, 187, 152, 39);
				window.getContentPane().add(btnDownload);
				final Scanner sc = new Scanner(System.in);
				Boolean isReceivingInput = true;

				System.out.println("Peer ID is: " + peerId);
				ListenServer server;
				ConcurrentMap<ByteBuffer, TorrentHandler> torrentMap;

				torrentMap = new ConcurrentHashMap<ByteBuffer, TorrentHandler>();
				server = ListenServer.create(torrentMap);
				port = server.getListenPort();
				System.out.println("Listening on port: " + port);

				Thread listener =  new Thread(server);
				listener.start();

				TorrentHandler myTorrent = null;
					
					myTorrent = TorrentHandler.create(textField.getText(),textField_1.getText());
					if (myTorrent != null) {
						torrentMap.put(myTorrent.info.info_hash, myTorrent);
						new Thread(myTorrent).start();
					} else {
						System.err.println("Couldn't start torrent: " + textField.getText());
						JOptionPane.showMessageDialog(window, "Couldn't Start Torrent!","Torrent Read Error",JOptionPane.ERROR_MESSAGE);
						isReceivingInput = false;
						server.shutdown();
					}
				
					textField.setVisible(false);
					textField.setEditable(false);
					textField_1.setVisible(false);
					textField_1.setEditable(false);
					btnDownload.setVisible(false);
					btnDownload.setEnabled(false);
				
					
				    window.getContentPane().removeAll();
				if (isReceivingInput == true) {
					window.setTitle(myTorrent.getFilename());
					final JLabel fileLabel = new JLabel("File Being Downloaded: "+myTorrent.getFilename());
					fileLabel.setBounds(70, 70, 200, 40);
					window.getContentPane().add(fileLabel);
					JProgressBar progressbar = new JProgressBar(0,100);
					progressbar.setVisible(true);
					progressbar.setStringPainted(true);
					final JLabel percentDownload = new JLabel(String.format("%.2f %% downloaded", myTorrent.getDownloadPercentage()));
					percentDownload.setBounds(20, 100, 300, 40);
					progressbar.setBounds(40,150,300,40);
					window.getContentPane().add(percentDownload);
					window.getContentPane().add(progressbar);
					myTorrent.addObserver(new Observer() {
						@Override
						public void update(Observable o, Object arg) {
							String text = String.format("%.2f %% downloaded", ((Double)arg).doubleValue());
							percentDownload.setText(text);
							progressbar.setValue(((Double)arg).intValue());
						}
					});
					window.getContentPane().repaint();
				}

				while (isReceivingInput && sc.hasNextLine()) {
					String input = sc.nextLine();
					
					if (input.equalsIgnoreCase("exit"))
						isReceivingInput = false;
					else if (input.equalsIgnoreCase("status"))
						myTorrent.status();
				}
				sc.close();
				server.shutdown();
				myTorrent.shutdown();
				System.out.println("RUBTClient closing");
				
					
					
			}
		});
	
	
	window.setVisible(true);
	

	}
	}

