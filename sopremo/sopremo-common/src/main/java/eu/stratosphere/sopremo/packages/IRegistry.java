package eu.stratosphere.sopremo.packages;

import java.util.Set;

import eu.stratosphere.sopremo.ISopremoType;
import eu.stratosphere.sopremo.operator.Name;

/**
 * A registry to manage elements, such as constants, methods, and operators.
 * 
 * @param <T>
 *        the type of the elements
 */
// Implementation note: for now, it does not seem necessary to implement Map
// However, all signatures are at least as strict as the corresponding Map methods to ease future change
public interface IRegistry<T> extends ISopremoType /* , Map<String, T> */{

	/**
	 * Returns the elements which is registered with the given name or {@code null}.
	 * 
	 * @param name
	 *        the name of the element
	 * @return the elements or {@code null}
	 */
	public T get(Name name);

	/**
	 * Returns the elements which is registered with the given name or {@code null}.
	 * 
	 * @param name
	 *        the name of the element
	 * @return the elements or {@code null}
	 */
	public T get(String name);

	public NameChooser getNameChooser();

	/**
	 * Returns the set of all names.
	 * 
	 * @return the set of all names
	 */
	public Set<String> keySet();

	/**
	 * Registers a new element with the given name.<br>
	 * If there is already an element with the given name, this method may throw an {@link IllegalArgumentException}.
	 * 
	 * @param name
	 *        the name of the element
	 * @param element
	 *        the element itself
	 */
	public void put(Name name, T element);

	/**
	 * Registers a new element with the given name.<br>
	 * If there is already an element with the given name, this method may throw an {@link IllegalArgumentException}.
	 * 
	 * @param name
	 *        the name of the element
	 * @param element
	 *        the element itself
	 */
	public void put(String name, T element);
}
