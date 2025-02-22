/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jigasi.util;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.utils.*;
import org.jitsi.utils.concurrent.*;
import org.jitsi.xmpp.extensions.*;
import org.jivesoftware.smack.bosh.*;
import org.jivesoftware.smack.packet.*;

import java.lang.reflect.*;
import java.util.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.math.*;
import java.security.*;
import java.util.concurrent.*;

/**
 * Various utility methods.
 *
 * @author Pawel Domas
 */
public class Util
{
    /**
     * Returns <tt>MediaFormat</tt> of the first {@link CallPeer} that belongs
     * to given {@link Call}(if peer and formats are available).
     *
     * @param call the {@link Call} to which the call peer for whom we want to
     *             retrieve the media format belongs to.
     */
    public static MediaFormat getFirstPeerMediaFormat(Call call)
    {
        if (!(call instanceof MediaAwareCall))
            return null;

        MediaAwareCall mediaCall = (MediaAwareCall) call;
        if (mediaCall.getCallPeerCount() == 0)
            return null;

        CallPeer peer
            = (CallPeer) mediaCall.getCallPeerList().get(0);
        if (!(peer instanceof MediaAwareCallPeer))
            return null;

        MediaAwareCallPeer mediaPeer
            = (MediaAwareCallPeer) peer;

        CallPeerMediaHandler peerMediaHndl
            = mediaPeer.getMediaHandler();
        if (peerMediaHndl == null)
            return null;

        MediaStream peerStream
            = peerMediaHndl.getStream(MediaType.AUDIO);

        if (peerStream == null)
            return null;

        return peerStream.getFormat();
    }

    /**
     * Call resource currently has the form of e23gr547@callcontro.server.net.
     * This methods extract random call id part before '@' sign. In the example
     * above it is 'e23gr547'.
     * @param callResource the call resource/URI from which the call ID part
     *                     will be extracted.
     * @return extracted random call ID part from full call resource string.
     */
    public static String extractCallIdFromResource(String callResource)
    {
        return callResource.substring(0, callResource.indexOf("@"));
    }

    /**
     * Get the md5 hash of a string
     *
     * received from:
     * https://stackoverflow.com/questions/415953/how-can-i-generate-an-md5-hash
     *
     * @param toHash the string to generate the MD5 hash from
     * @return the md5 hash of the given string
     */
    public static String stringToMD5hash(String toHash)
    {
        try
        {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.reset();
            m.update(toHash.getBytes());
            byte[] digest = m.digest();
            BigInteger bigInt = new BigInteger(1, digest);
            String hashtext = bigInt.toString(16);

            // Now we need to zero pad it if you actually want the full
            // 32 chars.
            if (hashtext.length() < 32)
            {
                int padLength = 32 - hashtext.length();
                String pad = String.join("",
                    Collections.nCopies(padLength, "0"));
                hashtext = pad + hashtext;
            }

            return hashtext;
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Pretty print an XML string into human-readable format
     * (by adding indentation)
     *
     * Retrieved from: https://stackoverflow.com/a/1264912
     *
     * @param input the XML to format, as String
     * @param indent the indent to apply to the XML
     * @return the formatted String
     */
    public static String prettyFormatXMLString(String input, int indent)
    {
        try
        {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory
                = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Extracts bosh connection sessionId if possible.
     * @param connection the bosh connection which sessionId we will try to
     * extract.
     * @return the sessionId if extracted or null.
     */
    public static Object getConnSessionId(Object connection)
    {
        Field myField = getField(XMPPBOSHConnection.class, "sessionID");

        if (myField != null)
        {
            myField.setAccessible(true);
            try
            {
                return myField.get(connection);
            }
            catch( Exception e)
            {
                Logger.getLogger(Util.class).error("cannot read it", e);
            }
        }

        return null;
    }

    /**
     * Utility method to get the field from a class.
     * @param clazz the class.
     * @param fieldName the field men.
     * @return the field or null.
     */
    private static Field getField(Class clazz, String fieldName)
    {
        try
        {
            return clazz.getDeclaredField(fieldName);
        }
        catch (NoSuchFieldException e)
        {
            Class superClass = clazz.getSuperclass();
            if (superClass == null)
            {
                return null;
            }
            else
            {
                return getField(superClass, fieldName);
            }
        }
    }

    /**
     * Makes a new RTP {@code RawPacket} filled with padding with the specified
     * parameters. Note that because we're creating a packet filled with
     * padding, the length must not exceed 12 + 0xFF.
     *
     * @param ssrc the SSRC of the RTP packet to make.
     * @param pt the payload type of the RTP packet to make.
     * @param seqNum the sequence number of the RTP packet to make.
     * @param ts the RTP timestamp of the RTP packet to make.
     * @param len the length of the RTP packet to make.
     * @return the RTP {@code RawPacket} that was created.
     */
    public static RawPacket makeRTP(
        long ssrc, int pt, int seqNum, long ts, int len)
    {
        byte[] buf = new byte[len];

        RawPacket pkt = new RawPacket(buf, 0, buf.length);

        pkt.setVersion();
        pkt.setPayloadType((byte) pt);
        pkt.setSSRC((int) ssrc);
        pkt.setTimestamp(ts);
        pkt.setSequenceNumber(seqNum);

        return pkt;
    }

    /**
     * Creates new thread pool.
     * @param size the pool size.
     * @param name the threads name prefix.
     * @return the newly created pool.
     */
    public static ExecutorService createNewThreadPool(int size, String name)
    {
        return new ThreadPoolExecutor(
            1, size,
            60L, TimeUnit.SECONDS, // time to wait before clearing threads
            new LinkedBlockingQueue<>(),
            new CustomizableThreadFactory(name, true));
    }

    /**
     * Creates a feature xmpp extension, that can be added to the features and used in presence.
     * @param var the value to be added.
     * @return the extension element.
     */
    public static ExtensionElement createFeature(String var)
    {
        AbstractPacketExtension feature = new AbstractPacketExtension(null, "feature"){};
        feature.setAttribute("var", var);

        return feature;
    }
}
