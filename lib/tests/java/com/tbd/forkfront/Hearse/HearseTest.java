package com.tbd.forkfront.Hearse;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;

import com.tbd.forkfront.Hearse.Hearse.HearseHeader;
import com.tbd.forkfront.Hearse.Hearse.HearseResponse;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HearseTest {

    private MockWebServer server;
    private Hearse hearse;
    private SharedPreferences mockPrefs;
    private Activity mockActivity;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        mockActivity = mock(Activity.class);
        mockPrefs = mock(SharedPreferences.class);
        Resources mockResources = mock(Resources.class);

        when(mockActivity.getResources()).thenReturn(mockResources);
        when(mockResources.getString(anyInt())).thenReturn("mockValue");
        when(mockResources.getBoolean(anyInt())).thenReturn(true);
        when(mockPrefs.getString(anyString(), anyString())).thenReturn("mockToken");

        hearse = new Hearse(mockActivity, mockPrefs, "/tmp");
        Hearse.BASE_URL = server.url("/").toString() + "?act=";
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void testGetStringMD5() {
        String input = "NetHack";
        String expected = "36fce685b7f5227e45f4d85d5df48386";
        String actual = Hearse.getStringMD5(input);
        assertEquals("MD5 mismatch for 'NetHack'", expected, actual);
    }

    @Test
    public void testDoGetHeaders() throws Exception {
        server.enqueue(new MockResponse().setBody("OK").addHeader("X-HEARSE", "true"));

        List<HearseHeader> headers = new ArrayList<>();
        headers.add(new HearseHeader("X_TEST", "Value"));

        hearse.doGet(Hearse.BASE_URL, "testaction", headers);

        RecordedRequest request = server.takeRequest();
        assertEquals("/?act=testaction", request.getPath());
        assertEquals("GET", request.getMethod());
        assertEquals("Value", request.getHeader("X_TEST"));
        assertNotNull(request.getHeader("X_HEARSECRC"));
        assertNotNull(request.getHeader("X_CLIENTID"));
    }

    @Test
    public void testDoPostHeaders() throws Exception {
        server.enqueue(new MockResponse().setBody("OK").addHeader("X-HEARSE", "true"));

        List<HearseHeader> headers = new ArrayList<>();
        headers.add(new HearseHeader("X_POST_TEST", "PostValue"));
        byte[] data = "some data".getBytes();

        hearse.doPost(Hearse.BASE_URL, "postaction", headers, data);

        RecordedRequest request = server.takeRequest();
        assertEquals("/?act=postaction", request.getPath());
        assertEquals("POST", request.getMethod());
        assertEquals("PostValue", request.getHeader("X_POST_TEST"));
        assertEquals("some data", request.getBody().readUtf8());
    }

    @Test
    public void testHearseResponseHeaderCaseInsensitivity() {
        java.util.Map<String, List<String>> headers = new java.util.HashMap<>();
        List<String> values = new java.util.ArrayList<>();
        values.add("true");
        headers.put("X-Hearse", values);

        HearseResponse resp = new HearseResponse(200, new byte[0], headers);

        // This will fail if the map is case-sensitive and we're not handling it
        assertNotNull("Should find header with exact match", resp.getFirstHeader("X-Hearse"));
        assertNotNull("Should find header with different case", resp.getFirstHeader("x-hearse"));
        assertNotNull("Should find header with all caps", resp.getFirstHeader("X-HEARSE"));
    }
}
