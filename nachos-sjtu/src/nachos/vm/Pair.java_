package nachos.vm;

public class Pair {
	public int first, second;

	public Pair(int first, int second) {
		super();
		this.first = first;
		this.second = second;
	}

	@Override
	public String toString() {
		return "Pair [first=" + first + ", second=" + second + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Pair))
			return false;
		Pair o = (Pair) obj;
		return o.first == first && o.second == second;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

}
