//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/.
// 

package inet.cdnlab;

import inet.nodes.inet.Router;
import inet.world.httptools.HttpController;
import inet.networklayer.autorouting.ipv4.IPv4NetworkConfigurator;
import inet.nodes.inet.StandardHost;
import inet.cdnlab.controller.CDNController;

network CDN_Infrastructure_test
{
    @display("bgb=900,400;bgi=maps/world");
    types:
        channel gigabitline extends ned.DatarateChannel
        {
            parameters:
                delay = 0.1us;
                datarate = 1000Mbps;
        }
    submodules:
        originServer1: StandardHost {
            @display("p=179,61;i=device/server,,0;is=vs");
        }
        surrogateServer1: StandardHost {
            parameters:
                @display("p=432,44;i=device/server2;is=vs");
                //IP_address_on_web = "69.73.128.0";
                //latitude = 29.7018;
                //longitude = -95.9137;
        }
        surrogateServer2: StandardHost {
            @display("p=285,314;i=device/server2;is=vs");
        }
        client: Client {
            @display("p=705,190;t=[28 ~ -90];is=vs");
            IP_address_on_web = "80.90.0.0";
            latitude = 10;
            longitude = 10;
            country = "LV";
            city = "Riga";
        }
        controller: CDNController {
            @display("p=30,23;i=block/cogwheel;is=vs");
        }
        configurator: IPv4NetworkConfigurator {
            @display("p=869,23");
        }
        //---------------------------------- ROUTERS
        asRouter_6508: AS_Router {
            parameters:
                @display("p=319,190;is=vs");
                @display("t=[US Camarillo-72.172.68.0]");
                as_name = "";
                IP_address_on_web = "72.172.68.0";
                latitude = 34.2041;
                longitude = -118.9807;
                country = "US";
                city = "Camarillo";
        }
        asRouter_209: AS_Router {
            parameters:
                @display("t=[US Jefferson City-24.32.114.0];p=179,190;is=vs");
                as_name = "";
                IP_address_on_web = "24.32.114.0";
                latitude = 38.4814;
                longitude = -92.1273;
                country = "US";
                city = "Jefferson City";
        }
        asRouter_3356: AS_Router {
            parameters:
                @display("t=[US -4.0.0.0];p=432,190;is=vs");
                as_name = "";
                IP_address_on_web = "4.0.0.0";
                latitude = 38;
                longitude = -97;
                country = "US";
                city = "";
        }
        asRouter_11042: AS_Router {
            parameters:
                @display("t=[US Fulshear-69.73.128.0];p=140,314;is=vs");
                as_name = "";
                IP_address_on_web = "69.73.128.0";
                latitude = 29.7018;
                longitude = -95.9137;
                country = "US";
                city = "Fulshear";
        }
        asRouter_21156: AS_Router {
            parameters:
                @display("t=[LV Riga-80.90.0.0];p=575,190;is=vs");
                as_name = "";
                IP_address_on_web = "80.90.0.0";
                latitude = 56.95;
                longitude = 24.1;
                country = "LV";
                city = "Riga";
        }
    connections:
        asRouter_209.ethg++ <--> gigabitline <--> asRouter_6508.ethg++;
        asRouter_3356.ethg++ <--> gigabitline <--> asRouter_6508.ethg++;
        asRouter_3356.ethg++ <--> gigabitline <--> asRouter_21156.ethg++;
        asRouter_209.ethg++ <--> gigabitline <--> asRouter_11042.ethg++;
        surrogateServer2.ethg++ <--> gigabitline <--> asRouter_11042.ethg++;
        asRouter_3356.ethg++ <--> gigabitline <--> surrogateServer1.ethg++;
        client.ethg++ <--> gigabitline <--> asRouter_21156.ethg++;
        originServer1.ethg++ <--> gigabitline <--> asRouter_209.ethg++;
}
