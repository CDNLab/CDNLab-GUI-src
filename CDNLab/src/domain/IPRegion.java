package domain;

public class IPRegion {
	private long startRange;
	private long endRange;
	private String countryCode;

	public IPRegion(long startRange, long endRange, String countryCode) {
		super();
		this.startRange = startRange;
		this.endRange = endRange;
		this.countryCode = countryCode;
	}

	public long getStartRange() {
		return startRange;
	}

	public void setStartRange(long startRange) {
		this.startRange = startRange;
	}

	public long getEndRange() {
		return endRange;
	}

	public void setEndRange(long endRange) {
		this.endRange = endRange;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

}
