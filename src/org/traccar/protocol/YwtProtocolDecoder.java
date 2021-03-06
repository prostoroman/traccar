/*
 * Copyright 2013 Anton Tananaev (anton.tananaev@gmail.com)
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
package org.traccar.protocol;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.Log;
import org.traccar.model.Position;

public class YwtProtocolDecoder extends BaseProtocolDecoder {

    public YwtProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    /**
     * Regular expressions pattern
     */
    //%GP,3000012345:0,090723182813,E114.602345,N22.069725,,30,160,4,0,00,,2794-10FF-46000,3>0-0
    static private Pattern pattern = Pattern.compile(
            "%(..)," +                     // Type
            "(\\d+):" +                    // Unit identifier
            "\\d+," +                      // Subtype
            "(\\d{2})(\\d{2})(\\d{2})" +   // Date (YYMMDD)
            "(\\d{2})(\\d{2})(\\d{2})," +  // Time (HHMMSS)
            "([EW])" +
            "(\\d{3}\\.\\d{6})," +         // Longitude (DDDMM.MMMM)
            "([NS])" +
            "(\\d{2}\\.\\d{6})," +         // Latitude (DDMM.MMMM)
            "(\\d+)?," +                   // Altitude
            "(\\d+)," +                    // Speed
            "(\\d+)," +                    // Course
            "(\\d+)," +                    // Satellite
            "([^,]+)," +                   // Report identifier
            "([0-9a-fA-F\\-]+)" +          // Status
            ".*");

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        String sentence = (String) msg;
        
        // Parse message
        Matcher parser = pattern.matcher(sentence);
        if (!parser.matches()) {
            return null;
        }
        
        // Create new position
        Position position = new Position();
        StringBuilder extendedInfo = new StringBuilder("<protocol>ywt</protocol>");
        Integer index = 1;
        String type = parser.group(index++);

        // Device
        String id = parser.group(index++);
        try {
            position.setDeviceId(getDataManager().getDeviceByImei(id).getId());
        } catch(Exception error) {
            Log.warning("Unknown device - " + id);
            return null;
        }
        
        // Time
        Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        time.clear();
        time.set(Calendar.YEAR, 2000 + Integer.valueOf(parser.group(index++)));
        time.set(Calendar.MONTH, Integer.valueOf(parser.group(index++)) - 1);
        time.set(Calendar.DAY_OF_MONTH, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.HOUR, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));
        position.setTime(time.getTime());

        // Longitude
        String hemisphere = parser.group(index++);
        Double lonlitude = Double.valueOf(parser.group(index++));
        if (hemisphere.compareTo("W") == 0) lonlitude = -lonlitude;
        position.setLongitude(lonlitude);

        // Latitude
        hemisphere = parser.group(index++);
        Double latitude = Double.valueOf(parser.group(index++));
        if (hemisphere.compareTo("S") == 0) latitude = -latitude;
        position.setLatitude(latitude);
        
        // Altitude
        String altitude = parser.group(index++);
        if (altitude != null) {
            position.setAltitude(Double.valueOf(altitude));
        } else {
            position.setAltitude(0.0);
        }

        // Speed
        position.setSpeed(Double.valueOf(parser.group(index++)));

        // Course
        position.setCourse(Double.valueOf(parser.group(index++)));
        
        // Satellites
        int satellites = Integer.valueOf(parser.group(index++));
        position.setValid(satellites >= 3);
        extendedInfo.append("<satellites>").append(satellites).append("</satellites>");
        
        // Report identifier
        String reportId = parser.group(index++);
        
        // Status
        extendedInfo.append("<status>");
        extendedInfo.append(parser.group(index++));
        extendedInfo.append("</status>");

        // Send response
        if (type.equals("KP") || type.equals("EP") || type.equals("EP")) {
            if (channel != null) {
                channel.write("%AT+" + type + "=" + reportId + "\r\n");
            }
        }
        
        position.setExtendedInfo(extendedInfo.toString());
        return position;
    }

}
