public interface IDatabase {
	/**
	 * Checks if the specified user exists in the database
	 * 
	 * @param username
	 *            the username to check
	 * @return true if the user exists, false otherwise
	 */
	public boolean userExists(String username);

	/**
	 * Checks if the specified password is correct for the current user
	 * 
	 * @param username
	 *            the user account
	 * @param password
	 *            the password to verify
	 * @return true if the password is correct, false otherwise
	 */
	public boolean passwordCorrect(String username, String password);

	/**
	 * Gets the locked status of the user's maildrop
	 * 
	 * @param username
	 *            the user account
	 * @return true if the maildrop is locked, false otherwise
	 */
	public boolean getMaildropLocked(String username);

	/**
	 * Sets the locked status of the user's maildrop
	 * 
	 * @param username
	 *            the user account
	 * @param locked
	 *            true if the maildrop should be locked
	 */
	public void setMaildropLocked(String username, boolean locked);

	/**
	 * Deletes messages marked to be deleted from the maildrop
	 * 
	 * @param username
	 *            the user account
	 * @return the number of messages deletes from the maildrop
	 */
	public int deleteMarkedMessages(String username);

	/**
	 * Gets the number of messages in the user's maildrop
	 * 
	 * @param username
	 *            the user account
	 * @param deleted
	 *            true if the function should include messages marked as
	 *            deleted, false otherwise
	 * @return the number of messages in the user's maildrop
	 */
	public int numMessages(String username, boolean deleted);

	/**
	 * Gets the size of the user's entire maildrop, excluding messages marked to
	 * be deleted
	 * 
	 * @param username
	 *            the user account
	 * @return the size of the maildrop in octets
	 */
	public int sizeOfMaildrop(String username);

	/**
	 * Gets the size of message at the specified ID, in octets.
	 * 
	 * @param username
	 *            the user account
	 * @param id
	 *            the id of the message to get the size of
	 * @return the size of the message in octets or -1 if the message doesn't
	 *         exist
	 */
	public int sizeOfMessage(String username, int id);

	/**
	 * Checks if the specified message exists in the user's maildrop
	 * 
	 * @param username
	 *            the user account
	 * @param id
	 *            the id of the message to check
	 * @return true is the message exists, false otherwise
	 */
	public boolean messageExists(String username, int id);

	/**
	 * Sets the marked for deletion status of the specified message
	 * 
	 * @param username
	 *            the user account
	 * @param id
	 *            the id of the message
	 * @param marked
	 *            the marked for deletion state of the message
	 */
	public void setMark(String username, int id, boolean marked);

	/**
	 * Checks if the specified message is marked to be deleted
	 * 
	 * @param username
	 *            the user account
	 * @param id
	 *            the if of the message to check
	 * @return true if the message is marked to be deleted
	 */
	public boolean messageMarked(String username, int id);

	/**
	 * Returns the specified message as a string, potentially containing
	 * multiple lines
	 * 
	 * @param username
	 *            the user account
	 * @param id
	 *            the id of the message to retrieve
	 * @return a string representation of the message, null if a message with
	 *         the specified id doesn't exist
	 */
	public String getMessage(String username, int id);

	/**
	 * Gets the UIDL of the specified message as a String
	 * 
	 * @param username
	 *            the user account
	 * @param id
	 *            the id of the message to get the UIDL for
	 * @return the UIDL of the specified message
	 */
	public String messageUIDL(String username, int id);

	/**
	 * Restores all messages marked to be deleted
	 * 
	 * @param username
	 *            the userr acount
	 */
	public void restoreMarked(String username);

	/**
	 * Closes the database connection
	 */
	public void close();
}