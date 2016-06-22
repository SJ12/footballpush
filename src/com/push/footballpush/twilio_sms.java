//file to send sms
package com.push.footballpush;
import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.MessageFactory;
import com.twilio.sdk.resource.instance.Message;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;

public class twilio_sms{

  // Find your Account Sid and Token at twilio.com/console
  public static final String ACCOUNT_SID = "AC4ec60036a7dce19b008bb82e5c2925f";
  public static final String AUTH_TOKEN = "64a8aea84bb7c91da584d365960a45dd";
  public String to="";
  public String message1="";

  public twilio_sms(String to, String message) throws TwilioRestException
  {
        this.to = to;
        this.message1 = message;
        send_message();
  }

  public void send_message() throws TwilioRestException {
    TwilioRestClient client = new TwilioRestClient(ACCOUNT_SID, AUTH_TOKEN);

    // Build a filter for the MessageList
    List<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new BasicNameValuePair("Body", message1));
    params.add(new BasicNameValuePair("To", to));
    params.add(new BasicNameValuePair("From", "+1 256-344-8754"));

    MessageFactory messageFactory = client.getAccount().getMessageFactory();
    Message message = messageFactory.create(params);
    System.out.println(message.getSid());
  }


}
