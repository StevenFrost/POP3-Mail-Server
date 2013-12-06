import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class ServerThread extends Thread {
	/* System Messages */
	private static final String INFO_USER_TIMEOUT = "User timed out";
	private static final String INFO_USER_CONNECTED = "User connected";
	private static final String INFO_USER_DISCONNECTED = "User disconnected";
	private static final String SERVER_WELCOME = "+OK POP3 server ready";
	private static final String ERROR_STREAM = "Unable to open or close the network stream.";
	private static final String ERROR_SOCKET_STREAM_CLOSE = "Unable to close a socket or stream.";
	
	/* Thread members */
	private Socket socket;
	private InputStreamReader streamReader;
	private PrintWriter out;
	private BufferedReader in;
	private ICommandInterpreter interpreter;

	/**
	 * Initialises the thread with a name and sets the timeout for socket
	 * 
	 * @param socket
	 *            the socket object for this thread
	 * @param timeout
	 *            the number of seconds of inactivity before closing the socket
	 * @throws SocketException
	 *             if a timeout cannot be established
	 */
	public ServerThread(Socket socket, int timeout) throws SocketException {
		super("Pop3ServerThread <" + socket.getInetAddress() + ">");
		
		/* Configure the class */
		this.socket = socket;
		interpreter = new CommandInterpreter();
		this.socket.setSoTimeout(timeout * 1000);
		
		/* Server connection message */
		System.out.println("[" + socket.getInetAddress() + "] "
				+ INFO_USER_CONNECTED);
	}

	/**
	 * Establishes input and output streams to/from the client and waits for
	 * user commands over the socket. Commands are then parsed using the
	 * CommandInterpreter and server responses are sent back over the network to
	 * the client.
	 * 
	 * If the specified timeout for the socket was reached, the user will be
	 * disconnected and the thread will end, closing any streams and sockets
	 * used in the execution of the thread.
	 */
	@Override
	public void run() {
		try {
			/* Initialise the network streams */
			streamReader = new InputStreamReader(socket.getInputStream());
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(streamReader);
			String input, output;

			/* Send a welcome message */
			out.println(SERVER_WELCOME);
			
			/* Thread-blocking while loop waits for commands from the client */
			while ((input = in.readLine()) != null) {
				/* Handle the client command */
				output = interpreter.handleInput(input);
				out.println(output);
				
				/* Check for a QUIT command */
				if (input.startsWith("QUIT")) {
					break;
				}
			}
		} catch (SocketTimeoutException e) {
			interpreter.close();
			System.out.println("[" + socket.getInetAddress() + "] "
					+ INFO_USER_TIMEOUT);
		} catch (IOException e) {
			System.err.println(ERROR_STREAM);
		} finally {
			try {
				/* Close any open streams */
				streamReader.close();
				in.close();
				out.close();
				socket.close();
			} catch (IOException e) {
				System.err.println(ERROR_SOCKET_STREAM_CLOSE);
			} finally {
				System.out.println("[" + socket.getInetAddress() + "] "
						+ INFO_USER_DISCONNECTED);
			}
		}
	}
}