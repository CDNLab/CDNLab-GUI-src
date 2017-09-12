package domain;

import java.util.List;

public class IPRegionSet {
	private List<IPRegion> regions;

	public String findCountryCode(long long_ip) {

		String countryCode = "";
		int first = 0;
		int last = regions.size() - 1;
		int middle = (first + last) / 2;

		while (first <= last) {
			if (regions.get(middle).getStartRange() > long_ip)
				last = middle - 1;
			else if (regions.get(middle).getEndRange() < long_ip)
				first = middle + 1;
			else if (regions.get(middle).getStartRange() <= long_ip
					&& regions.get(middle).getEndRange() >= long_ip) {
			/*	System.out.println(regions.get(middle).getCountryCode()
						+ " found at location " + (middle + 1) + ".");*/
				countryCode=regions.get(middle).getCountryCode();
				break;
			}
			middle = (first + last) / 2;
		}
		if (first > last)
			//System.out.println("not found.\n");
			countryCode="";

		return countryCode;
	}

	public IPRegionSet(List<IPRegion> regions) {
		super();
		this.regions = regions;
	}

	public List<IPRegion> getRegions() {
		return regions;
	}

	public void setRegions(List<IPRegion> regions) {
		this.regions = regions;
	}

}
