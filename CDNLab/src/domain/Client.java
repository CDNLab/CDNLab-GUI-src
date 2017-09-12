package domain;

public class Client {
	private String name;
	private String IP_address_on_web;
	private String latitude;
	private String longitude;
	private String country;
	private String city;
	private String definition;

	public Client() {

	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIP_address_on_web() {
		return IP_address_on_web;
	}

	public void setIP_address_on_web(String iP_address_on_web) {
		IP_address_on_web = iP_address_on_web;
	}

	public String getLatitude() {
		return latitude;
	}

	public void setLatitude(String latitude) {
		this.latitude = latitude;
	}

	public String getLongitude() {
		return longitude;
	}

	public void setLongitude(String longitude) {
		this.longitude = longitude;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getDefinition() {
		return definition;
	}

	public void setDefinition(String definition) {
		this.definition = definition;
	}
	
	

}
