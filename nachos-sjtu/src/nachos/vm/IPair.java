package nachos.vm;

public class IPair {
	public int first, second;

	public IPair(int i, int j) {
		this.first = i;
		this.second = j;
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof IPair))
			return false;
		IPair o = (IPair) obj;
		return first == o.first && second == o.second;
	}

	@Override
	public String toString() {
		return String.valueOf(first) + String.valueOf(second);
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}
}
