public interface ICommandInterpreter {
	/**
	 * Parses and executes a POP3 command
	 * 
	 * @param input
	 *            the command, along with arguments as a string
	 * @return the message returned from the server
	 */
	public String handleInput(String input);

	/**
	 * Closes the database connection and releases the maildrop lock for the
	 * current user
	 */
	public void close();
}