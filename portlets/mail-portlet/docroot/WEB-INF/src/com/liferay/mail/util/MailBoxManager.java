/**
 * Copyright (c) 2000-2008 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.mail.util;

import com.liferay.mail.model.MailAccount;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.User;
import com.liferay.util.JSONUtil;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPSSLStore;
import com.sun.mail.imap.IMAPStore;

import java.io.IOException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.AndTerm;
import javax.mail.search.BodyTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.OrTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * <a href="MailBoxManager.java.html"><b><i>View Source</i></b></a>
 *
 * @author Scott Lee
 *
 */
public class MailBoxManager {

	public static MailAccount getMailAccount(User user, int mailAccountId) {
    	return new MailAccount(user, mailAccountId);
	}

	public static String getJSONAccounts(User user) throws MessagingException {
		JSONObject jsonObj = new JSONObject();

		// Accounts

		JSONArray jsonArray = new JSONArray();

		JSONUtil.put(jsonObj, "accounts", jsonArray);

		// Account 1

		JSONObject account1 = new JSONObject();

		JSONUtil.put(account1, "emailAddress", "liferay.mail.1@gmail.com");
		JSONUtil.put(account1, "accountId", "0");

		jsonArray.put(account1);

		// Account 2

		JSONObject account2 = new JSONObject();

		JSONUtil.put(account2, "emailAddress", "liferay.mail.2@gmail.com");
		JSONUtil.put(account2, "accountId", "1");

		jsonArray.put(account2);

		// Account 3

		JSONObject account3 = new JSONObject();

		JSONUtil.put(account3, "emailAddress", "liferay.mail.3@gmail.com");
		JSONUtil.put(account3, "accountId", "2");

		jsonArray.put(account3);

		// Accounts

		return jsonObj.toString();
	}

	public MailBoxManager(User user, int accountId) {
		_user = user;
		_mailAccount = new MailAccount(user, accountId);
	}

	public void createFolder(String folderName) throws Exception {
		Folder newFolder = getStore().getFolder(folderName);

		if (!newFolder.exists()) {
			newFolder.create(Folder.HOLDS_MESSAGES);
		}
	}

	public JSONObject deleteMessagesByUids(
			String folderName, String messageUids)
		throws MessagingException {

		Folder folder = getFolder(folderName);

		try {
			long[] messageUidsArray = GetterUtil.getLongValues(
				messageUids.split("\\s*,\\s*"));

			if (!folder.isOpen()) {
				folder.open(Folder.READ_WRITE);
			}

			for (long messageUid : messageUidsArray) {
				try {

        			// Delete from server

					Message message = getMessageByUid(folder, messageUid);

					message.setFlag(Flags.Flag.DELETED, true);

        			// Delete from local disc

        	    	String messagePath = MailDiscManager.getMessagePath(
    	    			_user, _mailAccount, folderName,
    	    			String.valueOf(messageUid));

    	        	FileUtil.deltree(messagePath);
				}
				catch (MessagingException me) {
					_log.error(me, me);
				}
			}

			folder.close(true);
		}
		catch (MessagingException me) {
			_log.error(me, me);
		}

		JSONObject jsonObj = new JSONObject();

		JSONUtil.put(jsonObj, "success", true);

		return jsonObj;
	}

	public Part getAttachment(
			String folderName, int messageUid, String contentPath)
		throws MessagingException {

		Message message = getMessageByUid(folderName, messageUid);

		return getMessagePart(message, contentPath);
	}

	public JSONObject markMessagesAsRead(
			String folderName, String messageUids, boolean read)
		throws MessagingException {

		IMAPFolder folder = (IMAPFolder)openFolder(folderName);

		long[] messageUidsArray = GetterUtil.getLongValues(
			messageUids.split("\\s*,\\s*"));

		if (!folder.isOpen()) {
			folder.open(Folder.READ_WRITE);
		}

		for (long messageUid : messageUidsArray) {
			try {

    			// Update message on server

				Message message = getMessageByUid(folder, messageUid);

				message.setFlag(Flags.Flag.SEEN, read);

    			// Update message on local disc

    			updateJSONMessageField(
    				_user, _mailAccount, folderName, String.valueOf(messageUid),
    				"read", String.valueOf(read));
    		}
    		catch (IOException ioe) {
    			_log.error(ioe, ioe);
			}
			catch (MessagingException me) {
				_log.error(me, me);
			}
		}

		folder.close(true);

		JSONObject jsonObj = new JSONObject();

		JSONUtil.put(jsonObj, "success", true);

		return jsonObj;
	}

    public JSONObject sendMessage(
			String messageType, String folderName, long messageUid, int fromAccountId, String to,
			String cc, String bcc, String subject, String content,
			Multipart multipart)
    	throws MessagingException {

		JSONObject jsonObj = new JSONObject();

		Message message;

    	if (messageType.equalsIgnoreCase("new")) {

    		// Instantiate a message

    		message = new MimeMessage(getSession());

    		send(message, fromAccountId, to, cc, bcc, subject, content, multipart);

    		JSONUtil.put(jsonObj, "success", true);
    	}
    	else {
			Message oldMessage = getMessageByUid(folderName, messageUid);

    		if (messageType.equalsIgnoreCase("forward")) {

    			// Create the message to forward

    			message = new MimeMessage(getSession());

    			// Create multipart to combine the parts

    			if (multipart == null) {
    				multipart = new MimeMultipart();
    			}

    			// Create and fill part for the forwarded content

    			BodyPart messageBodyPart = new MimeBodyPart();

    			messageBodyPart.setDataHandler(message.getDataHandler());

    			// Add part to multipart

    			multipart.addBodyPart(messageBodyPart);

    			send(oldMessage, fromAccountId, to, cc, bcc, subject, content, multipart);

    			JSONUtil.put(jsonObj, "success", true);
    		}
    		else if (messageType.equalsIgnoreCase("reply")) {
    			message = (MimeMessage)oldMessage.reply(false);

    			send(message, fromAccountId, to, cc, bcc, subject, content, multipart);

    			JSONUtil.put(jsonObj, "success", true);
    		}
    		else {
    			JSONUtil.put(jsonObj, "success", false);
    		}
    	}

		return jsonObj;
    }

    public void updateAccount() throws MessagingException {
    	List<Folder> folders = getFolders();

    	for (Folder folder : folders) {
			updateFolder(folder);
		}
    }

    public void updateFolder(Folder folder) throws MessagingException {

    	// Check if folder has been initialized

    	JSONObject jsonObj = MailDiscManager.getJSONFolder(
    		_user, _mailAccount, folder.getFullName());

    	try {
        	long latestMessageUid = MailDiscManager.getNewestStoredMessageUID(
        		_user, _mailAccount, folder.getFullName());

        	boolean initialized = ((jsonObj != null) &&
        		GetterUtil.getBoolean(jsonObj.get("initialized").toString()));

    		folder = openFolder(folder);

    		int messageCount = folder.getMessageCount();

    		Message[] messages;

    		if (!initialized || (latestMessageUid == -1)) {
        		if (messageCount < _messagesToPrefetch) {
            		messages = folder.getMessages(1, messageCount);
        		}
        		else {
            		messages = folder.getMessages(
        				(messageCount - _messagesToPrefetch + 1), messageCount);
        		}

            	storeMessagesToDisc(folder, messages);

        		// Write new JSON folder

        		storeFolderToDisc(folder, true, new Date());
        	}
        	else {

        		// Get new messages since last update

            	Message message = ((IMAPFolder)folder).getMessageByUID(
            		latestMessageUid);

            	int messageNumber = message.getMessageNumber();

            	messages = folder.getMessages(messageNumber, messageCount);

            	storeMessagesToDisc(folder, messages);
        		storeFolderToDisc(folder, true, new Date());
        	}
    	}
    	catch (JSONException jsone) {
    		_log.error(jsone, jsone);
    	}
    }

	protected String getAddresses(Address[] addresses) {
		StringBuilder sb = new StringBuilder();

		if (addresses == null) {
			return StringPool.BLANK;
		}

		try {
			for (int i = 0; i < addresses.length; i++) {
				InternetAddress address = (InternetAddress)addresses[i];

				if (i != 0) {
					sb.append(StringPool.COMMA);
				}

				sb.append(address.getAddress());
			}
		}
		catch (Exception e) {
			_log.error(e, e);

			return null;
		}

		return sb.toString();
	}

	protected void getBody(
			StringBuilder sb, String contentPath, Part messagePart,
			List<Object[]> attachments) {

		try {
			String contentType = messagePart.getContentType().toLowerCase();

			if (messagePart.getContent() instanceof Multipart) {

				// Multipart

				Multipart multipart = (Multipart)messagePart.getContent();

				for (int i = 0; i < multipart.getCount(); i++) {
					Part curPart = multipart.getBodyPart(i);

					if (getBodyMulitipart(
							contentType, curPart,
							contentPath + StringPool.PERIOD + i, sb,
							attachments)) {

						break;
					}
				}
			}
			else if (Validator.isNull(messagePart.getFileName())) {

				// Plain text, HTML or forwarded message

				if (contentType.startsWith(ContentTypes.TEXT_PLAIN)) {
					sb.append(messagePart.getContent());
					sb.append("\n\n");
				}
				else if (contentType.startsWith(ContentTypes.TEXT_HTML)) {
					sb.append((String)messagePart.getContent());
					sb.append("<hr />");
				}
				else if (contentType.startsWith(ContentTypes.MESSAGE_RFC822)) {
					getBody(
						sb, contentPath + StringPool.PERIOD + 0, messagePart,
						attachments);
				}
			}
			else {

				// Attachment

				attachments.add(
					new Object[] {
						contentPath + StringPool.PERIOD + -1,
						messagePart.getFileName()});
			}
		}
		catch (IOException ioe) {
			_log.error(ioe, ioe);
		}
		catch (MessagingException me) {
			sb.append("<hr />Error retrieving message content<hr />");

			_log.error(me, me);
		}
	}

	protected boolean getBodyMulitipart(
			String contentType, Part curPart, String contentPath,
			StringBuilder sb, List<Object[]> attachments)
		throws MessagingException {

		if (contentType.startsWith(ContentTypes.MULTIPART_ALTERNATIVE)) {
			String partContentType = curPart.getContentType().toLowerCase();

			if (partContentType.startsWith(ContentTypes.TEXT_HTML)) {
				getBody(sb, StringPool.BLANK, curPart, attachments);

				return true;
			}
		}
		else {
			getBody(sb, contentPath, curPart, attachments);
		}

		return false;
	}

	protected String getBodyPreview(String messageBody)
		throws MessagingException {

		messageBody = stripHtml(messageBody);

		if (messageBody.length() < 80) {
			return messageBody;
		}
		else {
			return messageBody.substring(0, 80).concat("...");
		}
	}

	protected Folder getFolder(String folderName) throws MessagingException {
		Folder folder = getStore().getDefaultFolder();

		folder = folder.getFolder(folderName);

		if (folder == null) {
			_log.error("Invalid folder " + folderName);
		}

		return folder;
	}

	protected List<Folder> getFolders() throws MessagingException {
		Store store = getStore();

		IMAPFolder rootFolder = (IMAPFolder)store.getDefaultFolder();

		List<Folder> allFolders = new ArrayList<Folder>();

		getFolders(allFolders, rootFolder.list());

		return allFolders;
	}

	protected void getFolders(List<Folder> list, Folder[] folders) {
		for (Folder folder : folders) {
			try {
				int folderType = folder.getType();

				if ((folderType & IMAPFolder.HOLDS_MESSAGES) != 0) {
					list.add(folder);
				}

				if ((folderType & IMAPFolder.HOLDS_FOLDERS) != 0) {
					getFolders(list, folder.list());
				}
			}
			catch (MessagingException me) {
				_log.error("Skipping IMAP folder: " + me.getMessage());
			}
		}
	}

	protected JSONArray getJSONAttachments(List<Object[]> attachments)
		throws MessagingException {

		JSONArray jsonArray = new JSONArray();

		for (Object[] attachment : attachments) {
			JSONArray tempJsonArray = new JSONArray();

			tempJsonArray.put(attachment[0]);
			tempJsonArray.put(attachment[1]);

			jsonArray.put(tempJsonArray);
		}

		return jsonArray;
	}

	protected JSONObject getJSONFolder(Folder folder)
		throws MessagingException {

		JSONObject jsonObj = new JSONObject();

		if (folder.getType() != Folder.HOLDS_FOLDERS) {
			openFolder(folder);

			JSONUtil.put(jsonObj, "messageCount", folder.getMessageCount());
			JSONUtil.put(jsonObj, "name", folder.getFullName());

			return jsonObj;
		}

		return null;
	}

	protected JSONObject getJSONMessage(Folder folder, Message message)
		throws MessagingException {

		SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy HH:mm");

		StringBuilder sb = new StringBuilder();

		List<Object[]> attachments = new ArrayList<Object[]>();

		getBody(sb, StringPool.BLANK, message, attachments);

		JSONObject jsonObj = new JSONObject();

		JSONUtil.put(jsonObj, "attachments", getJSONAttachments(attachments));
		JSONUtil.put(jsonObj, "body", sb.toString());
		JSONUtil.put(jsonObj, "bodyPreview", getBodyPreview(sb.toString()));
		JSONUtil.put(jsonObj, "date", sdf.format(message.getSentDate()));
		JSONUtil.put(jsonObj, "from", getAddresses(message.getFrom()));
		JSONUtil.put(jsonObj, "html", false);
		JSONUtil.put(jsonObj, "messageNumber", message.getMessageNumber());
		JSONUtil.put(jsonObj, "read", message.isSet(Flags.Flag.SEEN));
		JSONUtil.put(jsonObj, "subject", message.getSubject());
		JSONUtil.put(jsonObj, "uid", ((IMAPFolder)folder).getUID(message));

		JSONUtil.put(
			jsonObj, "to",
			getAddresses(message.getRecipients(RecipientType.TO)));
		JSONUtil.put(
			jsonObj, "cc",
			getAddresses(message.getRecipients(RecipientType.CC)));
		JSONUtil.put(
			jsonObj, "bcc",
			getAddresses(message.getRecipients(RecipientType.BCC)));

		return jsonObj;
	}

	protected void getIncomingStore(MailAccount mailAccount) {
		try {
			Properties props = new Properties();

			URLName url = new URLName(
				"imap", mailAccount.getMailInHostName(),
				GetterUtil.getInteger(mailAccount.getMailInPort()),
				StringPool.BLANK, mailAccount.getUsername(),
				mailAccount.getPassword());

			props.setProperty("mail.imap.port", mailAccount.getMailInPort());

			if (mailAccount.isMailSecure()) {
				props.setProperty(
					"mail.imap.socketFactory.port",
					mailAccount.getMailInPort());
				props.setProperty(
					"mail.imap.socketFactory.class", _SSL_FACTORY);
				props.setProperty("mail.imap.socketFactory.fallback", "false");
			}

			Session session = Session.getInstance(props, null);

			Store store = null;

			if (mailAccount.isMailSecure()) {
				store = new IMAPSSLStore(session, url);
			}
			else {
				store = new IMAPStore(session, url);
			}

			store.connect();

			setStore(store);
		}
		catch (MessagingException me) {
			if (_log.isErrorEnabled()) {
				_log.error(me, me);
			}
		}
	}

	protected Message getMessageByUid(String folderName, long messageUid)
		throws MessagingException {

		IMAPFolder folder = (IMAPFolder)openFolder(folderName);

		return getMessageByUid(folder, messageUid);
	}

	protected Message getMessageByUid(Folder folder, long messageUid)
		throws MessagingException {

		return ((IMAPFolder)folder).getMessageByUID(messageUid);
	}

	protected Part getMessagePart(Part part, String contentPath)
		throws MessagingException {

		int index = GetterUtil.getInteger(
			StringUtil.split(contentPath.substring(1), StringPool.PERIOD)[0]);

		try {
			if (part.getContent() instanceof Multipart) {
				String prefix = String.valueOf(index) + StringPool.PERIOD;

				Multipart multipart = (Multipart)part.getContent();

				for (int i = 0; i < multipart.getCount(); i++) {
					if (index == i) {
						return getMessagePart(
							multipart.getBodyPart(i),
							contentPath.substring(prefix.length()));
					}
				}
			}

			return part;
		}
		catch (IOException ioe) {
			_log.error(ioe, ioe);

			return null;
		}
	}

	protected Session getOutgoingSession(MailAccount mailAccount) {
		Properties props = new Properties();

		props.put("mail.smtp.host", mailAccount.getMailOutHostName());
		props.put("mail.smtp.port", mailAccount.getMailOutPort());

		if (mailAccount.isMailSecure()) {
			props.put(
				"mail.smtp.socketFactory.port", mailAccount.getMailOutPort());
			props.put("mail.smtp.socketFactory.class", _SSL_FACTORY);
			props.put("mail.smtp.socketFactory.fallback", "false");
			props.put("mail.smtp.auth", "true");
		}

		props.put("mail.debug", "false");

		Session session = Session.getDefaultInstance(props, null);

		session.setDebug(false);

		return session;
	}

	protected SearchTerm getSearchTerm(String searchString) {
		String searchStrings[] = searchString.split("\\s");

		SearchTerm[] allOrTerms = new OrTerm[searchStrings.length];

		for (int i = 0; i < searchStrings.length; i++) {
			String tempSearchString = searchStrings[i];

			SearchTerm[] allEmailPartsTerm = {
				new FromStringTerm(tempSearchString),
				new RecipientStringTerm(
					Message.RecipientType.TO, tempSearchString),
				new RecipientStringTerm(
					Message.RecipientType.CC, tempSearchString),
				new RecipientStringTerm(
					Message.RecipientType.BCC, tempSearchString),
				new BodyTerm(tempSearchString),
				new SubjectTerm(tempSearchString)
			};

			allOrTerms[i] = new OrTerm(allEmailPartsTerm);
		}

		return new AndTerm(allOrTerms);
	}

	protected Session getSession() {
		if (_session == null) {
			_session = getOutgoingSession(_mailAccount);
		}

		return _session;
	}

	protected Store getStore() {
		if (_store == null) {
			getIncomingStore(_mailAccount);
		}

		return _store;
	}

	protected Folder openFolder(String folderName) throws MessagingException {
		Folder folder = getStore().getDefaultFolder();

	    folder = folder.getFolder(folderName);

	    return openFolder(folder);
	}

	protected Folder openFolder(Folder folder) throws MessagingException {

		if (folder == null) {
			return null;
		}

	    if (folder.isOpen()) {
	    	return folder;
	    }

		try {
			folder.open(Folder.READ_WRITE);
		}
		catch (MessagingException me) {
	    	try {
				folder.open(Folder.READ_ONLY);
	    	}
	    	catch (MessagingException me2) {
				_log.error(
					"Unable to open folder " + folder.getFullName(), me2);
			}
		}

		return folder;
	}

	protected void send(
			Message message, int fromAccountId, String to, String cc,
			String bcc, String subject, String content, Multipart multipart)
		throws MessagingException {

		MailAccount fromMailAccount = new MailAccount(_user, fromAccountId);

		message.setSentDate(new Date());
		message.setFrom(new InternetAddress(fromMailAccount.getEmailAddress()));

		if (Validator.isNotNull(to)) {
			message.setRecipients(
				Message.RecipientType.TO, InternetAddress.parse(to, false));
		}

		if (Validator.isNotNull(cc)) {
			message.setRecipients(
				Message.RecipientType.CC, InternetAddress.parse(cc, false));
		}

		if (Validator.isNotNull(bcc)) {
			message.setRecipients(
				Message.RecipientType.BCC, InternetAddress.parse(bcc, false));
		}

		message.setSubject(subject);

		if (multipart != null) {
			message.setContent(multipart);
		}
		else {
			message.setText(content);
		}

		message.saveChanges();

		Session session = getOutgoingSession(fromMailAccount);

		Transport transport = session.getTransport("smtp");

		try {
			transport.connect(
				fromMailAccount.getUsername(), fromMailAccount.getPassword());

			transport.sendMessage(message, message.getAllRecipients());
		}
		finally {
			transport.close();
		}
	}

	protected void setStore(Store store) {
		_store = store;
	}

	protected JSONObject storeAccountToDisc(
			MailAccount account, boolean initialized, Date date) {

		try {
			JSONObject jsonObj = new JSONObject();

			String filepath = MailDiscManager.getAccountFilepath(
				_user, _mailAccount);

       		JSONUtil.put(jsonObj, "initialized", initialized);
    		JSONUtil.put(jsonObj, "lastUpdated", date);

    		FileUtil.write(filepath, jsonObj.toString());

			return jsonObj;
		}
		catch (IOException ioe) {
			_log.error(ioe, ioe);
		}

		return null;
	}

	protected JSONObject storeFolderToDisc(
			Folder folder, boolean initialized, Date date) {

		try {
			JSONObject jsonObj = getJSONFolder(folder);

			String filepath = MailDiscManager.getFolderFilepath(
				_user, _mailAccount, folder.getFullName());

       		JSONUtil.put(jsonObj, "initialized", initialized);
    		JSONUtil.put(jsonObj, "lastUpdated", date);

			FileUtil.write(filepath, jsonObj.toString());

			return jsonObj;
		}
		catch (IOException ioe) {
			_log.error(ioe, ioe);
		}
		catch (MessagingException me) {
			_log.error(me, me);
		}

		return null;
	}

	protected void storeMessagesToDisc(Folder folder, Message[] messages) {
		for (Message message : messages) {
			storeMessageToDisc(folder, message);
		}
	}

	protected void storeMessageToDisc(Folder folder, Message message) {
		try {
			IMAPFolder imapFolder = (IMAPFolder)folder;

			String jsonMessage = getJSONMessage(imapFolder, message).toString();
			String messageUid = String.valueOf(imapFolder.getUID(message));

			String filepath = MailDiscManager.getMessageFilepath(
				_user, _mailAccount, folder.getFullName(), messageUid);

			FileUtil.write(filepath, jsonMessage);
		}
		catch (IOException ioe) {
			_log.error(ioe, ioe);
		}
		catch (MessagingException me) {
			_log.error(me, me);
		}
	}

	protected String stripHtml(String html) {
		html = html.replaceAll( "<[^>]+>", StringPool.BLANK);
		html = html.replaceAll( "[\r\n]+", StringPool.BLANK);

		return html;
	}

	protected void updateJSONMessageField(
			User user, MailAccount mailAccount, String folderName,
			String messageUid, String field, String value)
		throws IOException {

    	JSONObject jsonObj = MailDiscManager.getJSONMessageByUid(
			_user, _mailAccount, folderName,
			String.valueOf(messageUid));

    	JSONUtil.put(jsonObj, field, value);

		String filepath = MailDiscManager.getMessageFilepath(
			_user, _mailAccount, folderName, messageUid);

		FileUtil.write(filepath, jsonObj.toString());
	}

	public static final String _SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";

	private static Log _log = LogFactory.getLog(MailBoxManager.class);

	private User _user;
	private MailAccount _mailAccount;
	private Session _session = null;
	private Store _store = null;
    private int _messagesToPrefetch = 20;

}