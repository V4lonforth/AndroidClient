package com.example.cliser;

import android.os.AsyncTask;

import com.loopj.android.http.*;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class PollTask extends AsyncTask<String, Void, String> {
    String ServerIP,Password;
    boolean Connection;
    int CID,SID;
    int CommandID;
    boolean Terminated;
    private byte[] Content;
    String RequestUri;
    AsyncHttpClient HTTPClient;
    AsyncHttpResponseHandler HTTPResponse;


    SimpleDateFormat DateFormat; //new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    SimpleDateFormat LocalDateFormat;

    HttpURLConnection urlConnection;

    long TimeCorrection;

    private JSONObject JContent;

    public PollTask(String aServerIP, String aPassword)
    {
        this.ServerIP = aServerIP;
        this.Password = aPassword;

        HTTPClient = new AsyncHttpClient();
        HTTPClient.setTimeout(15000);

        HTTPResponse = null;
        //CancelTokenSource = new CancellationTokenSource();
        RequestUri = String.format("http://{0}{1}", ServerIP, Protocol.URL);

        Connection = false;
        CID = 10000;
        SID = 0;
        CommandID = 10000;
        Terminated = false;

        DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        DateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        LocalDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    }

    private int IncCID() {
        if (CID < 0x40000000)
            CID++;
        else
            CID = 1;
        return CID;
    }

    private void NextPoll()
    {
        if (Terminated)
        {
            TerminatePoll();
        }
        else
        {
            PrepareRequest();
            SendRequestAsync();
        }
    }

    private void TerminatePoll()
    {
        if (HTTPClient != null)
        {
            HTTPClient.cancelAllRequests(false);
            //HTTPClient.Dispose();
        }
    }

    private void PrepareRequest()
    {
        String Nonce = Protocol.GetNonce();
        Date now = new Date();

        String CreationTime = DateFormat.format(new Date(now.getTime() + TimeCorrection));


        if (Math.abs(TimeCorrection) > 5)
        {
            SocketClient.chText("Синхронизация времени");
            JContent = Protocol.GetContent(IncCID(), SID, now);
        }
        else
        {
            JContent = Protocol.GetContent(IncCID(), SID);
        }
        try {

            String s = "<Envelope>\n  <Body>\n    <CID>10001</CID>\n    <SIDResp>0</SIDResp>\n  </Body>\n</Envelope>";
            Content = s.getBytes("UTF8");
            //Content = JContent.toString().getBytes("UTF8");

            String Digest = Protocol.GetDigest(Nonce, Password, Content, CreationTime);

            URL url = new URL(String.format("http://%s%s", ServerIP, Protocol.URL));
            urlConnection = (HttpURLConnection)url.openConnection();

            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("ECNC-Auth", String.format("Nonce=\"%s\", Created=\"%s\", Digest=\"%s\"", Nonce, CreationTime, Digest));
            urlConnection.setRequestProperty("Date", LocalDateFormat.format(now));
            urlConnection.setRequestProperty("Connection", "close");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestProperty("Accept-Encoding", "identity");

            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void SendRequestAsync()
    {
        try
        {
            if (JContent != null)
            {
                //WriteToLog(new XElement("Client", new XAttribute("LocalTime", DateTime.Now.ToLocalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")), XContent));
            }

            DataOutputStream dataOutputStream = new DataOutputStream(urlConnection.getOutputStream());
            dataOutputStream.write(Content);

            dataOutputStream.flush();
            dataOutputStream.close();

            int responseCode = urlConnection.getResponseCode();
            String response = urlConnection.getResponseMessage();

            urlConnection.disconnect();
        }
        catch(Exception e)
        {
            HTTPResponse = null;
        }
        //HandleResponse();
        NextPoll();
    }

    @Override
    protected String doInBackground(String... strings) {

//        SocketClient.chText("Начало опроса");
        NextPoll();
        return null;
    }

    /*private void HandleResponse()
    {
        boolean connection = false;
        if (!CancelTokenSource.IsCancellationRequested)
            if (HTTPResponse != null)
                if ((HTTPResponse.StatusCode == HttpStatusCode.OK) || (HTTPResponse.StatusCode == HttpStatusCode.Unauthorized))
                {
                    connection = true;
                    if (HTTPResponse.Headers.Date.HasValue)
                        TimeCorrection = HTTPResponse.Headers.Date.Value - DateTime.Now.ToUniversalTime();

                    try
                    {
                        XDocument Content = XDocument.Parse(HTTPResponse.Content.ReadAsStringAsync().Result);
                        if (Content.Root != null)
                        {
                            WriteToLog(new XElement("MBNet", new XAttribute("LocalTime", DateTime.Now.ToLocalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")), Content.Root));
                            var BodyNodes = Content.Element("Envelope").Element("Body").Elements();
                            foreach (var node in BodyNodes)
                            {
                                if (node.Name == "CIDResp") uint.TryParse(node.Value, out CIDResp);
                                if (node.Name == "SID") uint.TryParse(node.Value, out SID);
                                if (node.Name == "Events") HandleEvents(node);
                                if (node.Name == "DevStates") HandleDevStates(node);
                                if (node.Name == "OnlineStatus") HandleOnlineStatus(node);
                                if (node.Name == "UpdSysConfigResponse") HandleInitDevTree(node);
                                if (node.Name == "UpdAPBConfigResponse") HandleLoadAPB(node);
                                if (node.Name == "ChangesResults") HandleChangesResult(node);
                                if (node.Name == "ChangesResponse") HandleChangesResponse(node);
                                if (node.Name == "ErrCode") HandleError(node.Value);
                                if (node.Name == "ConfigGUID") CheckConfigGUID(node.Value);
                                if (node.Name == "ConnectedDevices") HandleConnectedDevices(node);
                                if (node.Name == "DisconnectedDevices") HandleDisconnectedDevices(node);
                                if (node.Name == "ConnectedMBNets") HandleConnectedMBNets(node);
                                if (node.Name == "DisconnectedMBNets") HandleDisconnectedMBNets(node);
                                if (node.Name == "ControlCmdsResponse") HandleControlCmdsResponse(node);
                                if (node.Name == "NumericalHWParams") HandleNumericalHWParams(node);
                            }
                            //totodo здесь нужно проверять наличие требуемого узла, чтобы завершать инициализацию при отсутствии ответов
                            //CheckInit();
                        }
                    }
                    catch(Exception e)
                    {
                    }
                }

        if (Connection != connection)
        {
            Connection = connection;

            if (Connection)
                SocketClient.chText("Восстановление связи");
        else
            SocketClient.chText("Потеря связи");
        }
    }*/
}
