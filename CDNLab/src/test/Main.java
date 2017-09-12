package test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import domain.CDNLabConfig;
import domain.IPRegionSet;
import domain.IPRegion;

public class Main {

	public static void main(String[] args) throws ClassNotFoundException, SQLException {
		CDNLabConfig cdnLabConfig = new CDNLabConfig();
		cdnLabConfig.setConnectionString("jdbc:mysql://localhost:3306/asn");
		cdnLabConfig.setDbUsername("root");
		Connection connection = CDNLabConfig.getConnection();
		List<IPRegion> ip_regions=new ArrayList<IPRegion>();
		
		String sql = "SELECT startRange,endRange,CountryCode FROM ip2location;";
		PreparedStatement statement = connection.prepareStatement(sql);
		ResultSet resultSet = statement.executeQuery();
		while(resultSet.next()){
			ip_regions.add(
					new IPRegion(
							resultSet.getLong("startRange"), 
							resultSet.getLong("endRange"), 
							resultSet.getString("CountryCode")
							)
					);
		}

		IPRegionSet ipRegionSet=new IPRegionSet(ip_regions);
		ipRegionSet.findCountryCode(new Long("3758090240"));//AU
		ipRegionSet.findCountryCode(new Long("3758090230"));//ID
		ipRegionSet.findCountryCode(new Long("630428000"));//RU
		ipRegionSet.findCountryCode(new Long("3288469500"));//BI
		ipRegionSet.findCountryCode(new Long("43046100"));//IR 
		ipRegionSet.findCountryCode(new Long("4304610045671"));//IR
		
		/*for(Region region:ip_regions){
			System.out.println(region.getCountryCode());
		}*/
	}

}
