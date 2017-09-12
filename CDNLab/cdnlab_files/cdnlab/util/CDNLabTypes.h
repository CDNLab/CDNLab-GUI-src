/**
 * Location Info item. Used in order to send locatio information of the client to server
 *
 */
#ifndef __INET_CDNLAB_TYPES_H
#define __INET_CDNLAB_TYPES_H
struct LocationInfo {
    std::string IP_address_on_web;
    double latitude;
    double longitude;
    std::string country;
    std::string city;
};
#endif
