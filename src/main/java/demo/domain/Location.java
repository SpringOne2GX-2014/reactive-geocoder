package demo.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.annotation.Id;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import reactor.util.ObjectUtils;

/**
 * @author Jon Brisbin
 */
@Document
public class Location implements Comparable<Location> {

	@Id
	private String   id;
	private String   name;
	private String   address;
	private String   city;
	private String   province;
	private String   postalCode;
	@GeoSpatialIndexed
	private double[] coordinates;

	public Location() {
	}

	public String getId() {
		return id;
	}

	public Location setId(String id) {
		this.id = id;
		return this;
	}

	public String getName() {
		return name;
	}

	public Location setName(String name) {
		this.name = name;
		return this;
	}

	public String getAddress() {
		return address;
	}

	public Location setAddress(String address) {
		this.address = address;
		return this;
	}

	public String getCity() {
		return city;
	}

	public Location setCity(String city) {
		this.city = city;
		return this;
	}

	public String getProvince() {
		return province;
	}

	public Location setProvince(String province) {
		this.province = province;
		return this;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public Location setPostalCode(String postalCode) {
		this.postalCode = postalCode;
		return this;
	}

	public double[] getCoordinates() {
		return coordinates;
	}

	public Location setCoordinates(double[] coordinates) {
		this.coordinates = coordinates;
		return this;
	}

	public Point toPoint() {
		return new Point(coordinates[0], coordinates[1]);
	}

	public String toJson(ObjectMapper mapper) {
		try {
			return mapper.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Location)) return false;

		Location location = (Location) o;

		if (address != null ? !address.equals(location.address) : location.address != null) return false;
		if (city != null ? !city.equals(location.city) : location.city != null) return false;
		if (coordinates != null ? !coordinates.equals(location.coordinates) : location.coordinates != null) return false;
		if (id != null ? !id.equals(location.id) : location.id != null) return false;
		if (postalCode != null ? !postalCode.equals(location.postalCode) : location.postalCode != null) return false;
		if (province != null ? !province.equals(location.province) : location.province != null) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = id != null ? id.hashCode() : 0;
		result = 31 * result + (address != null ? address.hashCode() : 0);
		result = 31 * result + (city != null ? city.hashCode() : 0);
		result = 31 * result + (province != null ? province.hashCode() : 0);
		result = 31 * result + (postalCode != null ? postalCode.hashCode() : 0);
		result = 31 * result + (coordinates != null ? ObjectUtils.nullSafeHashCode(coordinates) : 0);
		return result;
	}

	@Override
	public int compareTo(Location other) {
		int i = -1;

		if (null != id && null != other.getId()) {
			if (0 != (i = id.compareTo(other.getId()))) {
				return i;
			}
		}

		return i;
	}

	@Override
	public String toString() {
		return "Location{" +
				"id='" + id + '\'' +
				", name='" + name + '\'' +
				", address='" + address + '\'' +
				", city='" + city + '\'' +
				", province='" + province + '\'' +
				", postalCode='" + postalCode + '\'' +
				", coordinates=" + ObjectUtils.nullSafeToString(coordinates) +
				'}';
	}

}
