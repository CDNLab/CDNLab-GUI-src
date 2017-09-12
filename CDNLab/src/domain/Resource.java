package domain;

public class Resource {
	private String resourceName;
	private String size;

	public Resource(String resourceName, String size) {
		super();
		this.resourceName = resourceName;
		this.size = size;
	}

	public String getResourceName() {
		return resourceName;
	}

	public void setResourceName(String resourceName) {
		this.resourceName = resourceName;
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

}
