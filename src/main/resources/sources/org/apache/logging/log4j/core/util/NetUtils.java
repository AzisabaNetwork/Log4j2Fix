/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.util;

import java.io.File;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.ArrayUtils;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

/**
 * Networking-related convenience methods.
 */
public final class NetUtils {

    private static final Logger LOGGER = StatusLogger.getLogger();
    private static final String UNKNOWN_LOCALHOST = "UNKNOWN_LOCALHOST";

    private NetUtils() {
        // empty
    }

    /**
     * This method gets the network name of the machine we are running on. Returns "UNKNOWN_LOCALHOST" in the unlikely
     * case where the host name cannot be found.
     *
     * @return String the name of the local host
     */
    public static String getLocalHostname() {
        try {
            final InetAddress addr = InetAddress.getLocalHost();
            return addr == null ? UNKNOWN_LOCALHOST : addr.getHostName();
        } catch (final UnknownHostException uhe) {
            try {
                final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        final NetworkInterface nic = interfaces.nextElement();
                        final Enumeration<InetAddress> addresses = nic.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            final InetAddress address = addresses.nextElement();
                            if (!address.isLoopbackAddress()) {
                                final String hostname = address.getHostName();
                                if (hostname != null) {
                                    return hostname;
                                }
                            }
                        }
                    }
                }
            } catch (final SocketException se) {
                // ignore and log below.
            }
            LOGGER.error("Could not determine local host name", uhe);
            return UNKNOWN_LOCALHOST;
        }
    }

    /**
     * Returns all the local host names and ip addresses.
     * @return The local host names and ip addresses.
     */
    public static List<String> getLocalIps() {
        List<String> localIps = new ArrayList<>();
        localIps.add("localhost");
        localIps.add("127.0.0.1");
        try {
            final InetAddress addr = Inet4Address.getLocalHost();
            setHostName(addr, localIps);
        } catch (final UnknownHostException ex) {
            // Ignore this.
        }
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    final NetworkInterface nic = interfaces.nextElement();
                    final Enumeration<InetAddress> addresses = nic.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        final InetAddress address = addresses.nextElement();
                        setHostName(address, localIps);
                    }
                }
            }
        } catch (final SocketException se) {
            // ignore.
        }
        return localIps;
    }

    private static void setHostName(InetAddress address, List<String> localIps) {
        String[] parts = address.toString().split("\\s*/\\s*");
        if (parts.length > 0) {
            for (String part : parts) {
                if (Strings.isNotBlank(part) && !localIps.contains(part)) {
                    localIps.add(part);
                }
            }
        }
    }

    /**
     *  Returns the local network interface's MAC address if possible. The local network interface is defined here as
     *  the {@link java.net.NetworkInterface} that is both up and not a loopback interface.
     *
     * @return the MAC address of the local network interface or {@code null} if no MAC address could be determined.
     */
    public static byte[] getMacAddress() {
        byte[] mac = null;
        try {
            final InetAddress localHost = InetAddress.getLocalHost();
            try {
                final NetworkInterface localInterface = NetworkInterface.getByInetAddress(localHost);
                if (isUpAndNotLoopback(localInterface)) {
                    mac = localInterface.getHardwareAddress();
                }
                if (mac == null) {
                    final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                    if (networkInterfaces != null) {
                        while (networkInterfaces.hasMoreElements() && mac == null) {
                            final NetworkInterface nic = networkInterfaces.nextElement();
                            if (isUpAndNotLoopback(nic)) {
                                mac = nic.getHardwareAddress();
                            }
                        }
                    }
                }
            } catch (final SocketException e) {
                LOGGER.catching(e);
            }
            if (ArrayUtils.isEmpty(mac) && localHost != null) {
                // Emulate a MAC address with an IP v4 or v6
                final byte[] address = localHost.getAddress();
                // Take only 6 bytes if the address is an IPv6 otherwise will pad with two zero bytes
                mac = Arrays.copyOf(address, 6);
            }
        } catch (final UnknownHostException ignored) {
            // ignored
        }
        return mac;
    }

    /**
     * Returns the mac address, if it is available, as a string with each byte separated by a ":" character.
     * @return the mac address String or null.
     */
    public static String getMacAddressString() {
        final byte[] macAddr = getMacAddress();
        if (!ArrayUtils.isEmpty(macAddr)) {
            StringBuilder sb = new StringBuilder(String.format("%02x", macAddr[0]));
            for (int i = 1; i < macAddr.length; ++i) {
                sb.append(":").append(String.format("%02x", macAddr[i]));
            }
            return sb.toString();

        }
        return null;
    }

    private static boolean isUpAndNotLoopback(final NetworkInterface ni) throws SocketException {
        return ni != null && !ni.isLoopback() && ni.isUp();
    }

    /**
     * Converts a URI string or file path to a URI object.
     *
     * @param path the URI string or path
     * @return the URI object
     */
    public static URI toURI(final String path) {
        try {
            // Resolves absolute URI
            return new URI(path);
        } catch (final URISyntaxException e) {
            // A file path or a Apache Commons VFS URL might contain blanks.
            // A file path may start with a driver letter
            try {
                final URL url = new URL(path);
                return new URI(url.getProtocol(), url.getHost(), url.getPath(), null);
            } catch (MalformedURLException | URISyntaxException nestedEx) {
                return new File(path).toURI();
            }
        }
    }

}
