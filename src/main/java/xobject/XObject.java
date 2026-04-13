package xobject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

public class XObject {
	private static final String ID_CONSTANT = "id";

	private static final String CLASS_NAME_CONSTANT = "class";

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ExportClassName {
		String value();
	}

	/**
	 * This attribute sets the class that will be created based on an inner value of
	 * the class.
	 * 
	 * Used when importing data from a file.
	 * 
	 * This attribute expects the following static method exists in the class:
	 * Class<?> getClassFromParam(Object o)
	 */
	@Retention(RetentionPolicy.RUNTIME)
	public @interface ClassTypeFromParam {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface CollapseArray {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface OptionallyIdentified {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Identified {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Reference {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface MultiReference {
		Class<?>[] value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface SkipExportation {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface XSDFilter {
		String value() default "";
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface XSDEnumFilters {
		XSDEnumFilter[] value();
	}

	@Repeatable(XSDEnumFilters.class)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface XSDEnumFilter {
		String name();

		Class<?> value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ExportReference {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ExportAsString {

	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Optional {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface IgnoreExtraFields {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Emptiable {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface TextNode {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ChildTextNode {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface CheckType {
		Class<?> value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Name {
		String value();
	}

	/**
	 * @brief Attribute to perform automatic validation of the classes when
	 *        importing from a file.
	 * 
	 *        After reading from XML, JSON, ... the import class will call to
	 *        checkType function. So this function must be implemented
	 * 
	 * @code{.java}
	 * @PostCheckType(POO.class) public class POO { static public boolean
	 *                           checkType(SketchImagery sketchImagery) throws
	 *                           XObjectException { // ... return true; } }
	 * @endcode
	 */
	@Retention(RetentionPolicy.RUNTIME)
	public @interface PostCheckType {
		Class<?> value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface NormalizeInputValue {
		Class<?> value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ArraySplitter {
		Class<?> value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ArraySeparator {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface NormalizeArray {
		Class<?> value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ValueComparator {
		Class<?> value();

		int DecimalDigits() default 0;
	}

	public interface XObjectTruncatedComparison {
		void SetDecimalDigits(int value);
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ObjectMembersComparable {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface DescriptiveMember {

	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface XSDNestElement {

	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Comment {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface TrailingComment {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface EnumComment {
		Class<?> value();
	}

    public static class BasicTextNode {
        @TextNode
        public String text;

        public BasicTextNode(String text) {
            this.text = text;
        }
    }
    
	static private class InternalReference {
		final Object object;
		final Field field;
		final String id;

		InternalReference(Object object, Field field, String id) {
			this.object = object;
			this.field = field;
			this.id = id;
		}
	}

	private ArrayList<InternalReference> references = new ArrayList<InternalReference>();

	@SuppressWarnings("deprecation")
	private Object _parseXML(Node xmlObject, Class<?> clazz, XObjectStats stats) throws XObjectException {
		TreeMap<String, ArrayList<Node>> nodes = new TreeMap<String, ArrayList<Node>>();

		String textContent = "";

		if (xmlObject.getFirstChild() != null) {
			for (Node child = xmlObject.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child.getNodeType() == Element.TEXT_NODE ||
						child.getNodeType() == Node.CDATA_SECTION_NODE) {
					textContent += child.getTextContent();
				} else {
					if (child.getChildNodes().getLength() == 1
							&& (child.getChildNodes().item(0).getNodeType() == Element.TEXT_NODE ||
									child.getChildNodes().item(0).getNodeType() == Node.CDATA_SECTION_NODE)
							&& !child.getChildNodes().item(0).getNodeValue().trim().isEmpty()) {
						if (child.hasAttributes()) {
							Node unnecessaryChild = child.getChildNodes().item(0);

							child.setTextContent(unnecessaryChild.getNodeValue().trim());

							ArrayList<Node> list = nodes.get(child.getNodeName());

							if (list == null)
								nodes.put(child.getNodeName(), list = new ArrayList<Node>());

							list.add(child);
						} else {
							Node childTextNode = child.getChildNodes().item(0);

							ArrayList<Node> list = nodes.get(childTextNode.getNodeName());

							if (list == null)
								nodes.put(child.getNodeName(), list = new ArrayList<Node>());

							list.add(childTextNode);
						}
					} else {
						ArrayList<Node> list = nodes.get(child.getNodeName());

						if (list == null)
							nodes.put(child.getNodeName(), list = new ArrayList<Node>());

						list.add(child);
					}
				}
			}
		} else if (xmlObject.getNodeType() == Node.TEXT_NODE ||
				xmlObject.getNodeType() == Node.CDATA_SECTION_NODE) {
			textContent = xmlObject.getNodeValue();
		}

		textContent = textContent.trim();

		if (textContent.isEmpty())
			textContent = null;

		NamedNodeMap attributes = xmlObject.getAttributes();

		if (attributes != null) {
			for (int i = 0; i < attributes.getLength(); i++) {
				if (nodes.containsKey(attributes.item(i).getNodeName()))
					throw new XObjectException("Duplicated attribute " + attributes.item(i).getNodeName(), clazz,
							xmlObject);

				ArrayList<Node> list = new ArrayList<Node>();

				list.add(attributes.item(i));

				nodes.put(attributes.item(i).getNodeName(), list);
			}
		}

		TreeSet<String> foundAttributes = new TreeSet<String>(nodes.keySet());

		if (isAnnotationPresent(clazz, ExportClassName.class)) {
			ArrayList<Node> values = nodes.get(CLASS_NAME_CONSTANT);

			foundAttributes.remove(CLASS_NAME_CONSTANT);

			if (values == null || values.size() != 1)
				throw new XObjectException("This code should never be reached", clazz, xmlObject);

			try {
				clazz = Class.forName(clazz.getAnnotation(ExportClassName.class).value() + values.get(0).getNodeValue());
			} catch (ClassNotFoundException e) {
				throw new XObjectException(e, clazz, xmlObject);
			}
		} else if (isAnnotationPresent(clazz, ClassTypeFromParam.class)) {
			ClassTypeFromParam classTypeFromParam = clazz.getAnnotation(ClassTypeFromParam.class);

			ArrayList<Node> values = nodes.get(classTypeFromParam.value());

			foundAttributes.remove(classTypeFromParam.value());

			if (values == null || values.size() != 1)
				throw new XObjectException("This code should never be reached", clazz, xmlObject);

			try {
				Method getClassFromParamMethod = clazz.getMethod("getClassFromParam", Object.class);

				clazz = (Class<?>) getClassFromParamMethod.invoke(null, values.get(0).getNodeValue());
			}

			catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | DOMException e) {
				throw new XObjectException(e, clazz);
			}
		}

		Object newObject;

		try {
			newObject = clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new XObjectException(e, clazz, xmlObject);
		}

		boolean textContentWasUsed = false;

		for (Field field : getClassHierarchyFields(clazz)) {
			try {
				field.setAccessible(true);

				String fieldName = field.getName();

				if (field.isAnnotationPresent(Name.class))
					fieldName = field.getAnnotation(Name.class).value();

				foundAttributes.remove(fieldName);

				ArrayList<Node> values = nodes.get(fieldName);

				if (values == null) {
					if (field.isAnnotationPresent(TextNode.class)) {
					    if (textContent != null)
					    {
							if (field.isAnnotationPresent(ExportAsString.class)) 
							{
								setFieldValue(stats, field, newObject,
										createObjectFromString(stats, field.getType(), textContent, clazz), clazz);
							}
							else
							{
								setFieldValue(stats, field, newObject, textContent, clazz);
							}
					    }

						textContentWasUsed = true;
					} else {
						if (!field.isAnnotationPresent(Optional.class))
							throw new XObjectException("Required field", clazz, field, xmlObject);

						stats.reportUnusedField(newObject, field);
					}
				} else if (!field.getType().isArray()) {
					if (values.size() != 1)
						throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

					if (field.getAnnotation(CollapseArray.class) != null)
						throw new XObjectException("Found CollapseArray annotation for non-array element", clazz, field,
								xmlObject);

					if (field.getType() == String.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						boolean isEmptiable = field.isAnnotationPresent(Emptiable.class);

						String nodeValue = normalizeInputIfNecesary(values.get(0).getNodeValue(), field, clazz);

						if (nodeValue != null) {
							if (nodeValue.isEmpty()) {
								if (!isEmptiable)
									throw new XObjectException("Empty attribute is not emptiable", clazz, field,
											xmlObject);
							}

							if (field.isAnnotationPresent(CheckType.class)) {
								Class<?> type = field.getAnnotation(CheckType.class).value();

								checkBasicType(nodeValue, type, clazz, field, isEmptiable);
							} else if (field.getType().isAnnotationPresent(CheckType.class)) {
								Class<?> type = field.getType().getAnnotation(CheckType.class).value();

								checkBasicType(nodeValue, type, clazz, field, isEmptiable);
							}
						}

						setFieldValue(stats, field, newObject, nodeValue, clazz);
					} else if (field.getType() == int.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						String value = normalizeInputIfNecesary(values.get(0).getNodeValue(), field, clazz);

						setFieldValue(stats, field, newObject, Integer.parseInt(value), clazz);
					} else if (field.getType() == long.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						String value = normalizeInputIfNecesary(values.get(0).getNodeValue(), field, clazz);

						setFieldValue(stats, field, newObject, Long.parseLong(value), clazz);
					} else if (field.getType() == double.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						String value = normalizeInputIfNecesary(values.get(0).getNodeValue(), field, clazz);

						setFieldValue(stats, field, newObject, Double.parseDouble(value), clazz);
					} else if (field.getType() == float.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						String value = normalizeInputIfNecesary(values.get(0).getNodeValue(), field, clazz);

						setFieldValue(stats, field, newObject, Float.parseFloat(value), clazz);
					} else if (field.getType() == boolean.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						String value = normalizeInputIfNecesary(values.get(0).getNodeValue(), field, clazz);

						setFieldValue(stats, field, newObject, Boolean.parseBoolean(value), clazz);
					} else if (field.getType().isEnum()) {
						String value = normalizeInputIfNecesary(values.get(0).getNodeValue(), field, clazz);

						if (value == null || value.isEmpty()) {
							if (!field.isAnnotationPresent(Emptiable.class))
								throw new XObjectException("Unexpected empty value: "
										+ (value == null || value.isEmpty() ? "<empty>" : value), clazz, field);
						} else {
							Object[] constants = field.getType().getEnumConstants();

							boolean found = false;

							for (Object o : constants) {
								if (o.toString().equals(value)) {
									setFieldValue(stats, field, newObject, o, clazz);
									found = true;
									break;
								}
							}

							if (!found)
								throw new XObjectException(
										"Invalid enum value: " + (value.isEmpty() ? "<empty>" : value), clazz);
						}
					} else if (field.isAnnotationPresent(ExportAsString.class)) {
						String nodeValue = values.get(0).getNodeValue();

						setFieldValue(stats, field, newObject,
								createObjectFromString(stats, field.getType(), nodeValue, clazz), clazz);
					} else if (field.isAnnotationPresent(Reference.class)) {
						String nodeValue = values.get(0).getNodeValue();

						createReference(stats, newObject, field, normalizeInputIfNecesary(nodeValue, field, clazz),
								clazz);
					} else {
						setFieldValue(stats, field, newObject, _parseXML(values.get(0), field.getType(), stats), clazz);
					}
				} else {
					if (field.getAnnotation(CollapseArray.class) != null) {
						CollapseArray collapseArrayAnnotation = field.getAnnotation(CollapseArray.class);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						Node collapsedObject = values.get(0);

						values = new ArrayList<Node>();

						for (Node child = collapsedObject.getFirstChild(); child != null; child = child
								.getNextSibling()) {
							if (child.getNodeName().equals(collapseArrayAnnotation.value())) {
								if (field.isAnnotationPresent(TextNode.class)) {
									if (child.getChildNodes().getLength() == 1 && !child.hasAttributes()
											&& (child.getChildNodes().item(0).getNodeType() == Element.TEXT_NODE ||
													child.getChildNodes().item(0).getNodeType() == Node.CDATA_SECTION_NODE)
											&& !child.getChildNodes().item(0).getNodeValue().trim().isEmpty()) {
										values.add(child.getChildNodes().item(0));
									}
								} else {
									values.add(child);
								}
							}
						}
					}

					if (field.getType().getComponentType() == String.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						boolean isEmptiable = field.isAnnotationPresent(Emptiable.class);

						if (values.size() == 1 && values.get(0).getNodeValue().isEmpty()) {
							if (!isEmptiable)
								throw new XObjectException("Empty attribute is not emptiable", clazz, field, xmlObject);

							setFieldValue(stats, field, newObject, new String[0], clazz);
						} else {
							String[] valuesAsString;

							if (values.size() == 1) {
								String nodeValue = values.get(0).getNodeValue();

								valuesAsString = splitStringValue(stats, field, nodeValue, clazz);
							} else {
								valuesAsString = new String[values.size()];

								for (int i = 0; i < values.size(); i++)
									valuesAsString[i] = values.get(i).getTextContent();
							}

							String[] array = new String[valuesAsString.length];

							if (field.isAnnotationPresent(CheckType.class)) {
								Class<?> type = field.getAnnotation(CheckType.class).value();

								for (String s1 : valuesAsString)
									checkBasicType(s1, type, clazz, field, isEmptiable);
							} else if (field.getType().getComponentType().isAnnotationPresent(CheckType.class)) {
								Class<?> type = field.getType().getComponentType().getAnnotation(CheckType.class)
										.value();

								for (String s1 : valuesAsString)
									checkBasicType(s1, type, clazz, field, isEmptiable);
							}

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = normalizeInputIfNecesary(valuesAsString[i], field, clazz);

							Object[] objectsArray = (Object[]) normalizeArray(array, field, Object.class);

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = (String) objectsArray[i];

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == int.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new int[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							int[] array = new int[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Integer.parseInt(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == Integer.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new Integer[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							Integer[] array = new Integer[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Integer.parseInt(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == long.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new long[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							long[] array = new long[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Long.parseLong(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == Long.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new Long[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							Long[] array = new Long[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Long.parseLong(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == float.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new float[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							float[] array = new float[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Float.parseFloat(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == Float.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new Float[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							Float[] array = new Float[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Float.parseFloat(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == double.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new double[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							double[] array = new double[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Double
										.parseDouble(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == Double.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new Double[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							Double[] array = new Double[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Double
										.parseDouble(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == boolean.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new boolean[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							boolean[] array = new boolean[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Boolean
										.parseBoolean(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.getType().getComponentType() == Boolean.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field, xmlObject);

						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						if (nodeValue.isEmpty()) {
							setFieldValue(stats, field, newObject, new Boolean[0], clazz);
						} else {
							String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);

							Boolean[] array = new Boolean[valuesAsString.length];

							for (int i = 0; i < valuesAsString.length; i++)
								array[i] = Boolean
										.parseBoolean(normalizeInputIfNecesary(valuesAsString[i], field, clazz));

							setFieldValue(stats, field, newObject, array, clazz);
						}
					} else if (field.isAnnotationPresent(Reference.class)) {
						if (values.size() != 1)
							throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

						String nodeValue = values.get(0).getNodeValue();

						createReference(stats, newObject, field, normalizeInputIfNecesary(nodeValue, field, clazz),
								clazz);
					} else if (field.isAnnotationPresent(ExportAsString.class)) {
						Object[] array = (Object[]) Array.newInstance(field.getType().getComponentType(),
								values.size());

						for (int i = 0; i < values.size(); i++) {
							if (values.get(i) != null) {
								String value = values.get(i).getAttributes().getNamedItem("value").getNodeValue();

								array[i] = createObjectFromString(stats, field.getType().getComponentType(), value,
										clazz);
							}
						}

						array = normalizeArray(array, field, field.getType().getComponentType());

						setFieldValue(stats, field, newObject, array, clazz);
                    } else if (field.getType().getComponentType().isEnum()) {
                        if (values.size() != 1)
                            throw new XObjectException("Mismatch elements count", clazz, field, xmlObject);

                        String nodeValue = normalizeInputIfNecesary(values.get(0).getNodeValue(), field, clazz);
                        
                        String[] valuesAsString = splitStringValue(stats, field, nodeValue, clazz);
                        
                        Object[] array = (Object[]) Array.newInstance(field.getType().getComponentType(),
                                valuesAsString.length);

                        for (int i = 0; i < array.length; i++) {
                            if (valuesAsString[i] != null && !valuesAsString[i].isEmpty())
                            {
                                boolean found = false;
                                
                                for (Object enumConstant : field.getType().getComponentType().getEnumConstants())
                                {
                                    if (enumConstant.toString().equals(valuesAsString[i]))
                                    {
                                        array[i] = enumConstant;
                                        found = true;
                                        break;
                                    }
                                }
                                
                                if (!found)
                                    throw new XObjectException("Could not find enum constant for " + valuesAsString[i], clazz, field, xmlObject);
                            }
                        }

                        array = normalizeArray(array, field, field.getType().getComponentType());

                        setFieldValue(stats, field, newObject, array, clazz);
					} else {
						Object[] array = (Object[]) Array.newInstance(field.getType().getComponentType(),
								values.size());

						for (int i = 0; i < values.size(); i++) {
							if (values.get(i) != null)
								array[i] = _parseXML(values.get(i), field.getType().getComponentType(), stats);
						}

						array = normalizeArray(array, field, field.getType().getComponentType());

						setFieldValue(stats, field, newObject, array, clazz);
					}
				}
			} catch (InstantiationException | IllegalArgumentException | IllegalAccessException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new XObjectException(e, clazz, field, xmlObject);
			}
		}

		if (isAnnotationPresent(clazz, Identified.class) && nodes.get(ID_CONSTANT) == null) {
			throw new XObjectException("Could not find required identifier", clazz, xmlObject);
		} else if ((isAnnotationPresent(clazz, Identified.class)
				|| isAnnotationPresent(clazz, OptionallyIdentified.class)) && nodes.get(ID_CONSTANT) != null) {
			stats.identifyObject(nodes.get(ID_CONSTANT).get(0).getNodeValue(), newObject);

			foundAttributes.remove(ID_CONSTANT);
		}

		PostCheckType checkTypeAnnotation = clazz.getAnnotation(PostCheckType.class);

		if (checkTypeAnnotation != null) {
			Class<?> checkTypeClass = checkTypeAnnotation.value();

			try {
				Method method = checkTypeClass.getMethod("checkType", clazz);

				Boolean returnedValue = (Boolean) method.invoke(null, newObject);

				if (!returnedValue.booleanValue())
					throw new XObjectException(
							"Invalid value " + newObject + " after checking type using " + checkTypeClass, clazz);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				throw new XObjectException(e, clazz);
			}
		}

		if (!textContentWasUsed && textContent != null) {
			stats.reportExtraAttributes(newObject, foundAttributes.toArray(new String[0]));

			throw new XObjectException("TextNode attribute was not found '" + textContent.trim() + "'", clazz);
		}

		if (!clazz.isAnnotationPresent(IgnoreExtraFields.class) && !foundAttributes.isEmpty()) {
			stats.reportExtraAttributes(newObject, foundAttributes.toArray(new String[0]));

			throw new XObjectException("Attributes were not found " + foundAttributes, clazz);
		}

		return newObject;
	}

	private void setFieldValue(XObjectStats stats, Field field, Object object, Object value, Class<?> clazz)
			throws IllegalArgumentException, IllegalAccessException {
        field.set(object, value);

        if (stats != null)
			stats.reportFieldAssignment(object, field, value);
	}

	private String[] splitStringValue(XObjectStats stats, Field field, String nodeValue, Class<?> clazz)
			throws XObjectException {
		ArraySplitter arraySplitter = field.getAnnotation(ArraySplitter.class);

		if (arraySplitter != null) {
			try {
				Method method = arraySplitter.value().getMethod("splitArray", String.class, IdentifiedObjects.class);

				return (String[]) method.invoke(null, nodeValue, stats.identifiedObjects);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				throw new XObjectException(e, clazz, field);
			}
		} else {
			String symbol = ",";

			ArraySeparator arraySeparator = field.getAnnotation(ArraySeparator.class);

			if (arraySeparator != null)
				symbol = arraySeparator.value();

			return nodeValue.split(symbol);
		}
	}

	static public String normalizeInputIfNecesary(String nodeValue, Field field, Class<?> clazz)
			throws XObjectException {
		NormalizeInputValue normalizer = field.getAnnotation(NormalizeInputValue.class);

		if (normalizer != null) {
			try {
				Method method = normalizer.value().getMethod("normalize", String.class);

				return (String) method.invoke(null, nodeValue);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				throw new XObjectException(e, clazz, field);
			}
		}

		return nodeValue;
	}

	@SuppressWarnings("unchecked")
	private <T> T[] normalizeArray(T[] array, Field field, Class<?> clazz) throws XObjectException {
		NormalizeArray normalizer = field.getAnnotation(NormalizeArray.class);

		if (normalizer != null) {
			try {
				Method method = normalizer.value().getMethod("normalize", Object[].class);

				Object[] objectsArray = (Object[]) array;

				return (T[]) method.invoke(null, (Object) objectsArray);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				throw new XObjectException(e, clazz, field);
			}
		}

		return array;
	}

	private void checkBasicType(String objectString, Class<?> type, Class<?> clazz, Field field, boolean isEmptiable)
			throws XObjectException {
		try {
			objectString = objectString.trim();

			if (type.equals(int.class) || type.equals(Integer.class))
				Integer.parseInt(objectString);

			else if (type.equals(long.class) || type.equals(Long.class))
				Long.parseLong(objectString);

			else if (type.equals(float.class) || type.equals(Float.class))
				Float.parseFloat(objectString);

			else if (type.equals(double.class) || type.equals(Double.class))
				Double.parseDouble(objectString);

			else if (type.equals(boolean.class) || type.equals(Boolean.class))
				Boolean.parseBoolean(objectString);

			else if (!objectString.isEmpty()) {
				if (type.isEnum()) {
					Object[] constants = type.getEnumConstants();

					boolean found = false;

					for (Object o : constants) {
						if (o.toString().equals(objectString))
							found = true;
					}

					if (!found)
						throw new XObjectException(
								"Invalid value: " + (objectString.isEmpty() ? "<empty>" : objectString), clazz, field);
				} else {
					try {
						Method method = type.getMethod("checkString", String.class);

						Boolean returnedValue = (Boolean) method.invoke(null, objectString);

						if (!returnedValue.booleanValue())
							throw new XObjectException(
									"Invalid value: " + (objectString.isEmpty() ? "<empty>" : objectString), clazz,
									field);
					} catch (NoSuchMethodException e) {
						try {
							Field stringArrayField = type.getDeclaredField("values");

							String[] stringArray = (String[]) stringArrayField.get(null);

							if (Arrays.asList(stringArray).indexOf(objectString) == -1)
								throw new XObjectException(
										"Invalid value: " + (objectString.isEmpty() ? "<empty>" : objectString), clazz,
										field);
						} catch (NoSuchFieldException e1) {
							try {
								Field regexField = type.getDeclaredField("pattern");

								String regex = (String) regexField.get(null);

								if (!objectString.matches(regex))
									throw new XObjectException(
											"Invalid value: " + (objectString.isEmpty() ? "<empty>" : objectString),
											clazz, field);
							} catch (NoSuchFieldException e2) {
								throw new XObjectException(
										"Error checking type against " + type + " (value: " + objectString + ")", clazz,
										field);
							}
						}
					}
				}
			} else if (!isEmptiable) {
				throw new XObjectException("Empty attribute is not emptiable", clazz, field);
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
			throw new XObjectException(e, clazz, field);
		}
	}

	public static <T> T parseXML(InputStream inputStream, Class<T> clazz) throws XObjectException, IOException {
		return parseXML(inputStream, clazz, new XObjectStats());
	}

	public static <T> T parseXML(InputStream inputStream, Class<T> clazz, XObjectStats stats)
			throws XObjectException, IOException {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

			Document doc = builder.parse(inputStream);

			XObject xObject = new XObject();

			@SuppressWarnings("unchecked")
			T object = (T) xObject._parseXML(doc.getDocumentElement(), clazz, stats);

			xObject.solveReferences(stats, clazz);

			return object;
		} catch (SAXException | ParserConfigurationException e) {
			throw new XObjectException(e, clazz);
		}
	}

	public static <T> T parseXML(File xml, Class<T> clazz) throws XObjectException, IOException {
		return parseXML(xml, clazz, new XObjectStats());
	}

	public static <T> T parseXML(File xml, Class<T> clazz, XObjectStats stats) throws XObjectException, IOException {
		FileInputStream inputStream = new FileInputStream(xml);

		T object = parseXML(inputStream, clazz, stats);

		inputStream.close();

		return object;
	}

	public static <T> T parseXML(String xml, Class<T> clazz) throws XObjectException, IOException {
		return parseXML(new ByteArrayInputStream(xml.getBytes("UTF-8")), clazz);
	}

	private void _toXML(Document doc, Element xmlObject, Object object, XObjectStats stats, boolean transformXML)
			throws XObjectException {
		Class<? extends Object> clazz = object.getClass();

		if (!transformXML && isAnnotationPresent(clazz, Identified.class))
			xmlObject.setAttribute(ID_CONSTANT, stats.createIdentifier(object));

		if (isAnnotationPresent(clazz, ExportClassName.class))
		{
			String className = object.getClass().getName();
			
			String classNamePrefix = clazz.getAnnotation(ExportClassName.class).value();
			
			if (!className.startsWith(classNamePrefix))
				throw new XObjectException("Class " + className + " do not have prefix " + classNamePrefix, clazz);
			
			className = className.substring(classNamePrefix.length());
			
			xmlObject.setAttribute(CLASS_NAME_CONSTANT, className);
		}

		for (Field field : getClassHierarchyFields(clazz)) {
			try {
				field.setAccessible(true);

				String fieldName = field.getName();

				if (field.isAnnotationPresent(Name.class))
					fieldName = field.getAnnotation(Name.class).value();

				Object subObject = field.get(object);

				if (field.isAnnotationPresent(SkipExportation.class)) {
					// Do nothing
				} else if (subObject == null) {
					if (!field.isAnnotationPresent(Optional.class) && !field.isAnnotationPresent(Emptiable.class))
						throw new XObjectException("Required field", clazz, field);
				} else if (!field.getType().isArray()) {
					if (field.getType().equals(String.class) || field.getType().equals(int.class)
							|| field.getType().equals(Integer.class) || field.getType().equals(long.class)
							|| field.getType().equals(Long.class) || field.getType().equals(double.class)
							|| field.getType().equals(Double.class) || field.getType().equals(float.class)
							|| field.getType().equals(Float.class) || field.getType().equals(boolean.class)
							|| field.getType().equals(Boolean.class) || field.getType().isEnum()) {
						if (field.isAnnotationPresent(TextNode.class)) {
							Text newElement = doc.createTextNode(subObject.toString());

							xmlObject.appendChild(newElement);
						} else if (field.isAnnotationPresent(ChildTextNode.class)) {
							Element newElement = doc.createElement(fieldName);

							newElement.setTextContent(subObject.toString());

							xmlObject.appendChild(newElement);
						} else {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							xmlObject.setAttribute(fieldName, subObject.toString());
						}
					} else if (transformXML && field.isAnnotationPresent(ExportReference.class)) {
						Element groupingElement = doc.createElement(fieldName);

						Element newElement = doc.createElement(field.getType().getSimpleName());

						_toXML(doc, newElement, subObject, stats, transformXML);

						groupingElement.appendChild(newElement);

						xmlObject.appendChild(groupingElement);
					} else if (field.isAnnotationPresent(Reference.class)) {
						if (!transformXML) {
							String identifier = stats.createIdentifier(subObject);

							xmlObject.setAttribute(fieldName, identifier);
						}
					} else if (field.isAnnotationPresent(ExportAsString.class)) {
						xmlObject.setAttribute(fieldName, toString(subObject, stats));
					} else {
						Element newElement = doc.createElement(fieldName);

						_toXML(doc, newElement, subObject, stats, transformXML);

						xmlObject.appendChild(newElement);
					}
				} else {
					if (field.isAnnotationPresent(CollapseArray.class)) {
						CollapseArray annotation = field.getAnnotation(CollapseArray.class);

						Element groupingElement = doc.createElement(fieldName);

						Object[] subObjects = (Object[]) subObject;

						if (field.isAnnotationPresent(TextNode.class)) {
							for (Object o : subObjects) {
								Element newElement = doc.createElement(annotation.value());

								newElement.setTextContent(o.toString());

								groupingElement.appendChild(newElement);
							}
						} else {
							for (int i = 0; i < subObjects.length; i++) {
								Element newElement = doc.createElement(annotation.value());

								if (field.isAnnotationPresent(ExportAsString.class)) {
									if (subObjects[i] != null)
										newElement.setAttribute("value", subObjects[i].toString());
								} else {
									if (subObjects[i] != null)
										_toXML(doc, newElement, subObjects[i], stats, transformXML);
								}

								groupingElement.appendChild(newElement);
							}
						}

						xmlObject.appendChild(groupingElement);
					} else {
						String separator = ",";

						if (field.isAnnotationPresent(ArraySeparator.class))
							separator = field.getAnnotation(ArraySeparator.class).value();

						if (field.getType().getComponentType() == int.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							int[] subObjects = (int[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == Integer.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							Integer[] subObjects = (Integer[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == long.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							long[] subObjects = (long[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == Long.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							Long[] subObjects = (Long[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == float.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							float[] subObjects = (float[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == Float.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							Float[] subObjects = (Float[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == double.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							double[] subObjects = (double[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == Double.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							Double[] subObjects = (Double[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == boolean.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							boolean[] subObjects = (boolean[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else if (field.getType().getComponentType() == Boolean.class) {
							if (field.isAnnotationPresent(Reference.class))
								throw new XObjectException("Unexpected @Reference annotation", clazz, field);

							Boolean[] subObjects = (Boolean[]) field.get(object);

							String[] array = new String[subObjects.length];

							for (int i = 0; i < subObjects.length; i++)
								array[i] = String.valueOf(subObjects[i]);

							xmlObject.setAttribute(fieldName, String_join(separator, array));
						} else {
							Object[] subObjects = (Object[]) field.get(object);

							if (transformXML && field.isAnnotationPresent(ExportReference.class)) {
								Element groupingElement = doc.createElement(fieldName);

								String objectName = field.getType().getComponentType().getSimpleName();

								for (int i = 0; i < subObjects.length; i++) {
									Element newElement = doc.createElement(objectName);

									if (subObjects[i] != null)
										_toXML(doc, newElement, subObjects[i], stats, transformXML);

									groupingElement.appendChild(newElement);
								}

								xmlObject.appendChild(groupingElement);
							} else if (field.isAnnotationPresent(Reference.class)) {
								if (!transformXML) {
									String[] identifiers = new String[subObjects.length];

									for (int i = 0; i < subObjects.length; i++)
										identifiers[i] = stats.createIdentifier(subObjects[i]);

									xmlObject.setAttribute(fieldName, String_join(separator, identifiers));
								}
							} else if (field.isAnnotationPresent(CollapseArray.class)) {
								for (int i = 0; i < subObjects.length; i++) {
									Element newElement = doc.createElement(fieldName);

									if (subObjects[i] != null)
										_toXML(doc, newElement, subObjects[i], stats, transformXML);

									xmlObject.appendChild(newElement);
								}
							} else if (field.getType().getComponentType().equals(String.class)) {
								xmlObject.setAttribute(fieldName, String_join(separator, (String[]) subObjects));
							} else {
								for (int i = 0; i < subObjects.length; i++) {
									Element newElement = doc.createElement(fieldName);

									if (subObjects[i] != null) {
										if (field.isAnnotationPresent(ExportAsString.class)) {
											if (subObjects[i] != null)
												newElement.setAttribute(fieldName, subObjects[i].toString());
										} else {
											if (subObjects[i] != null)
												_toXML(doc, newElement, subObjects[i], stats, transformXML);
										}
									}

									xmlObject.appendChild(newElement);
								}
							}
						}
					}
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new XObjectException(e, clazz, field);
			}
		}
	}

	public static void toXML(Object object, OutputStream writer, boolean transformXML) throws XObjectException {
	    Class<? extends Object> clazz = object.getClass();

	    String rootName = clazz.getSimpleName();
	    
	    if (clazz.isAnnotationPresent(Name.class))
	        rootName = clazz.getAnnotation(Name.class).value();
	    
		toXML(object, rootName, writer, transformXML);
	}

	public static void toXML(Object object, String rootName, OutputStream writer, boolean transformXML)
			throws XObjectException {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

			Document doc = builder.newDocument();

			Element rootElement = doc.createElement(rootName);

			doc.appendChild(rootElement);

			XObjectStats stats = new XObjectStats();

			new XObject()._toXML(doc, rootElement, object, stats, transformXML);

			TransformerFactory tf = TransformerFactory.newInstance();
			tf.setAttribute("indent-number", new Integer(2));
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

			DOMSource domSource = new DOMSource(doc);

			StreamResult result = new StreamResult(writer);
			transformer.transform(domSource, result);
		} catch (TransformerException | ParserConfigurationException e) {
			throw new XObjectException(e, object.getClass());
		}
	}

	public static String toXML(Object object, boolean transformXML) throws XObjectException {
		try {
			ByteArrayOutputStream writer = new ByteArrayOutputStream();

			toXML(object, writer, transformXML);

			return writer.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new XObjectException(e, object.getClass());
		}
	}

	public static void toXML(Object object, File file, boolean transformXML) throws XObjectException, IOException {
		FileOutputStream writer = new FileOutputStream(file);

		toXML(object, writer, transformXML);

		writer.close();
	}

	public static void toXML(Object object, String rootName, File file, boolean transformXML)
			throws XObjectException, IOException {
		FileOutputStream writer = new FileOutputStream(file);

		toXML(object, rootName, writer, transformXML);

		writer.close();
	}

	public static <T> T parseJSON(InputStream inputStream, Class<T> clazz) throws IOException, XObjectException {
		return parseJSON(inputStream, clazz, null);
	}

	public static <T> T parseJSON(InputStream inputStream, Class<T> clazz, XObjectStats stats) throws XObjectException {
		try {
			JSONTokener tokener = new JSONTokener(new InputStreamReader(inputStream, "UTF-8"));

			JSONObject jsonObject = new JSONObject(tokener);

			XObject xObject = new XObject();

			@SuppressWarnings("unchecked")
			T object = (T) xObject._parseJSON(jsonObject, clazz, stats);

			xObject.solveReferences(stats, clazz);

			return object;
		} catch (UnsupportedEncodingException e) {
			throw new XObjectException(e, clazz);
		}
	}

	public static <T> T parseJSON(File xml, Class<T> clazz) throws IOException, XObjectException {
		return parseJSON(xml, clazz, null);
	}

	public static <T> T parseJSON(File xml, Class<T> clazz, XObjectStats stats) throws IOException, XObjectException {
		if (xml.length() == 0)
			throw new XObjectException("Empty file", clazz);

		FileInputStream inputStream = new FileInputStream(xml);

		T object = parseJSON(inputStream, clazz, stats);

		inputStream.close();

		return object;
	}

	public static <T> T parseJSON(String xml, Class<T> clazz) throws XObjectException {
		try {
			return parseJSON(new ByteArrayInputStream(xml.getBytes("UTF-8")), clazz, null);
		} catch (UnsupportedEncodingException e) {
			throw new XObjectException(e, clazz);
		}
	}
	public static <T> T parseJSON(String xml, Class<T> clazz, XObjectStats stats) throws XObjectException {
		try {
			return parseJSON(new ByteArrayInputStream(xml.getBytes("UTF-8")), clazz, stats);
		} catch (UnsupportedEncodingException e) {
			throw new XObjectException(e, clazz);
		}
	}

	private Object _parseJSON(JSONObject jsonObject, Class<?> clazz, XObjectStats stats) throws XObjectException {
		TreeSet<String> foundAttributes = new TreeSet<String>(jsonObject.keySet());

		if (isAnnotationPresent(clazz, ExportClassName.class)) {
			String key = foundAttributes.floor(CLASS_NAME_CONSTANT);

			if (!key.equals(CLASS_NAME_CONSTANT))
				throw new XObjectException("This code should never be reached", clazz);

			foundAttributes.remove(CLASS_NAME_CONSTANT);

			try {
				clazz = Class.forName(clazz.getAnnotation(ExportClassName.class).value() + jsonObject.get(key).toString());
			} catch (ClassNotFoundException e) {
				throw new XObjectException(e, clazz);
			}
		} else if (isAnnotationPresent(clazz, ClassTypeFromParam.class)) {
			ClassTypeFromParam classTypeFromParam = clazz.getAnnotation(ClassTypeFromParam.class);

			Object value = jsonObject.get(classTypeFromParam.value());

			try {
				Method getClassFromParamMethod = clazz.getMethod("getClassFromParam", Object.class);

				clazz = (Class<?>) getClassFromParamMethod.invoke(null, value);
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new XObjectException(e, clazz);
			}
		}

		Object newObject;

		try {
			newObject = clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new XObjectException(e, clazz);
		}

		if (isAnnotationPresent(clazz, Identified.class) && jsonObject.optString(ID_CONSTANT) == null) {
			throw new XObjectException("Could not find required identifier", clazz);
		} else if ((isAnnotationPresent(clazz, Identified.class)
				|| isAnnotationPresent(clazz, OptionallyIdentified.class))
				&& jsonObject.optString(ID_CONSTANT) != null) {
			stats.identifyObject(jsonObject.getString(ID_CONSTANT), newObject);

			foundAttributes.remove(ID_CONSTANT);
		}

		for (Field field : getClassHierarchyFields(clazz)) {
			try {
				field.setAccessible(true);

				String fieldName = field.getName();

				if (field.isAnnotationPresent(Name.class))
					fieldName = field.getAnnotation(Name.class).value();

				foundAttributes.remove(fieldName);

				Object value = jsonObject.opt(fieldName);

				if (value == null) {
					if (!field.isAnnotationPresent(Optional.class))
						throw new XObjectException("Required field", clazz, field);
				} else if (!field.getType().isArray()) {
					if (field.getType() == String.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						boolean isEmptiable = field.isAnnotationPresent(Emptiable.class);

						String nodeValue = normalizeInputIfNecesary(value.toString(), field, clazz);

						if (nodeValue != null) {
							if (nodeValue.isEmpty()) {
								if (!isEmptiable)
									throw new XObjectException("Empty attribute is not emptiable", clazz, field);
							}

							if (field.isAnnotationPresent(CheckType.class)) {
								Class<?> type = field.getAnnotation(CheckType.class).value();

								checkBasicType(nodeValue, type, clazz, field, isEmptiable);
							} else if (field.getType().isAnnotationPresent(CheckType.class)) {
								Class<?> type = field.getType().getAnnotation(CheckType.class).value();

								checkBasicType(nodeValue, type, clazz, field, isEmptiable);
							}
						}

						if (value instanceof JSONObject || value instanceof JSONArray)
							throw new XObjectException("Required String field", clazz, field);

						setFieldValue(stats, field, newObject, nodeValue, clazz);
					} else if (field.getType() == int.class || field.getType() == Integer.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (value instanceof JSONObject || value instanceof JSONArray)
							throw new XObjectException("Required int field", clazz, field);

						setFieldValue(stats, field, newObject,
								Integer.parseInt(normalizeInputIfNecesary(value.toString(), field, clazz)), clazz);
					} else if (field.getType() == long.class || field.getType() == Long.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (value instanceof JSONObject || value instanceof JSONArray)
							throw new XObjectException("Required long field", clazz, field);

						setFieldValue(stats, field, newObject,
								Long.parseLong(normalizeInputIfNecesary(value.toString(), field, clazz)), clazz);
					} else if (field.getType() == double.class || field.getType() == Double.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (value instanceof JSONObject || value instanceof JSONArray)
							throw new XObjectException("Required double field", clazz, field);

						setFieldValue(stats, field, newObject,
								Double.parseDouble(normalizeInputIfNecesary(value.toString(), field, clazz)), clazz);
					} else if (field.getType() == float.class || field.getType() == Float.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (value instanceof JSONObject || value instanceof JSONArray)
							throw new XObjectException("Required float field", clazz, field);

						setFieldValue(stats, field, newObject,
								Float.parseFloat(normalizeInputIfNecesary(value.toString(), field, clazz)), clazz);
					} else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (value instanceof JSONObject || value instanceof JSONArray)
							throw new XObjectException("Required float field", clazz, field);

						setFieldValue(stats, field, newObject,
								Boolean.parseBoolean(normalizeInputIfNecesary(value.toString(), field, clazz)), clazz);
					} else if (field.isAnnotationPresent(Reference.class)) {
						if (value instanceof JSONObject || value instanceof JSONArray)
							throw new XObjectException("Required String field", clazz, field);

						this.references.add(new InternalReference(newObject, field,
								normalizeInputIfNecesary(value.toString(), field, clazz)));
					} else if (field.getType().isEnum()) {
						String sValue = normalizeInputIfNecesary(value.toString(), field, clazz);

						if (sValue == null || sValue.isEmpty()) {
							if (!field.isAnnotationPresent(Emptiable.class))
								throw new XObjectException(
										"Unexpected empty value: "
												+ (sValue == null || sValue.isEmpty() ? "<empty>" : sValue),
										clazz, field);
						} else {
							Object[] constants = field.getType().getEnumConstants();

							boolean found = false;

							for (Object o : constants) {
								if (o.toString().equals(sValue)) {
									setFieldValue(stats, field, newObject, o, clazz);
									found = true;
									break;
								}
							}

							if (!found)
								throw new XObjectException(
										"Invalid enum value: " + (sValue.isEmpty() ? "<empty>" : sValue), clazz, field);
						}
                    } else if (field.isAnnotationPresent(ExportAsString.class)) {
                        String sValue = normalizeInputIfNecesary(value.toString(), field, clazz);

                        if (sValue == null || sValue.isEmpty()) {
                            if (!field.isAnnotationPresent(Emptiable.class))
                                throw new XObjectException(
                                        "Unexpected empty value: "
                                                + (sValue == null || sValue.isEmpty() ? "<empty>" : sValue),
                                        clazz, field);
                        } else {
                            setFieldValue(stats, field, newObject, 
                                    createObjectFromString(stats, field.getType(), sValue,
                                    clazz), clazz);
                        }
					} else {
						if (!(value instanceof JSONObject))
							throw new XObjectException("Required JSONObject field", clazz, field);

						setFieldValue(stats, field, newObject, _parseJSON((JSONObject) value, field.getType(), stats),
								clazz);
					}
				} else {
					if (field.getType().getComponentType() == String.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (value instanceof JSONArray) {
    						JSONArray jsonArray = (JSONArray) value;
    
    						String[] array = new String[jsonArray.length()];
    
    						for (int i = 0; i < array.length; i++)
    							array[i] = normalizeInputIfNecesary(jsonArray.getString(i), field, clazz);
    
    						setFieldValue(stats, field, newObject, array, clazz);
						} else if (value instanceof String) {
						    String[] array = new String[]
    			            {
    			                normalizeInputIfNecesary((String) value, field, clazz)     
    			            };
    
                            setFieldValue(stats, field, newObject, array, clazz);
						} else {
						    throw new XObjectException("Required JSONArray field", clazz, field);
						}
					} else if (field.getType().getComponentType() == int.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (!(value instanceof JSONArray))
							throw new XObjectException("Required JSONArray field", clazz, field);

						JSONArray jsonArray = (JSONArray) value;

						int[] array = new int[jsonArray.length()];

						for (int i = 0; i < array.length; i++)
							array[i] = Integer.parseInt(normalizeInputIfNecesary(jsonArray.getString(i), field, clazz));

						setFieldValue(stats, field, newObject, array, clazz);
					} else if (field.getType().getComponentType() == long.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (!(value instanceof JSONArray))
							throw new XObjectException("Required JSONArray field", clazz, field);

						JSONArray jsonArray = (JSONArray) value;

						long[] array = new long[jsonArray.length()];

						for (int i = 0; i < array.length; i++)
							array[i] = Long.parseLong(normalizeInputIfNecesary(jsonArray.getString(i), field, clazz));

						setFieldValue(stats, field, newObject, array, clazz);
					} else if (field.getType().getComponentType() == float.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (!(value instanceof JSONArray))
							throw new XObjectException("Required JSONArray field", clazz, field);

						JSONArray jsonArray = (JSONArray) value;

						float[] array = new float[jsonArray.length()];

						for (int i = 0; i < array.length; i++)
							array[i] = Float.parseFloat(normalizeInputIfNecesary(jsonArray.getString(i), field, clazz));

						setFieldValue(stats, field, newObject, array, clazz);
					} else if (field.getType().getComponentType() == double.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (!(value instanceof JSONArray))
							throw new XObjectException("Required JSONArray field", clazz, field);

						JSONArray jsonArray = (JSONArray) value;

						double[] array = new double[jsonArray.length()];

						for (int i = 0; i < array.length; i++)
							array[i] = Double
									.parseDouble(normalizeInputIfNecesary(jsonArray.getString(i), field, clazz));

						setFieldValue(stats, field, newObject, array, clazz);
					} else if (field.getType().getComponentType() == boolean.class) {
						if (field.isAnnotationPresent(Reference.class))
							throw new XObjectException("Unexpected @Reference annotation", clazz, field);

						if (!(value instanceof JSONArray))
							throw new XObjectException("Required JSONArray field", clazz, field);

						JSONArray jsonArray = (JSONArray) value;

						boolean[] array = new boolean[jsonArray.length()];

						for (int i = 0; i < array.length; i++)
							array[i] = Boolean
									.parseBoolean(normalizeInputIfNecesary(jsonArray.getString(i), field, clazz));

						setFieldValue(stats, field, newObject, array, clazz);
                    } else if (field.getType().getComponentType().isEnum()) {
                        if (field.isAnnotationPresent(Reference.class))
                            throw new XObjectException("Unexpected @Reference annotation", clazz, field);

                        if (value instanceof JSONArray)
                        {
                            JSONArray jsonArray = (JSONArray) value;
    
                            Object[] array = (Object[]) Array.newInstance(field.getType().getComponentType(),
                                    jsonArray.length());
    
                            for (int i = 0; i < array.length; i++)
                            {
                                String sValue = normalizeInputIfNecesary(jsonArray.getString(i), field, clazz);
                                
                                Object[] constants = field.getType().getComponentType().getEnumConstants();
    
                                boolean found = false;
    
                                for (Object o : constants) {
                                	try 
                                	{
                                		String[] aliases = (String[]) o.getClass().getMethod("aliases").invoke(o);	
                                		
                                		for (String alias : aliases)
                                		{
                                			if (alias.equals(sValue))
                                			{
                                                array[i] = o;
                                                found = true;
                                                break;
                                            }
                                		}
                                	}
                                	catch(NoSuchMethodException e)
                                	{
                                		if (o.toString().equals(sValue))
                                		{
                                            array[i] = o;
                                            found = true;
                                            break;
                                        }
                                	}
                                }
    
                                if (!found)
                                	throw new XObjectException(
                                            "Invalid enum value: " + (sValue.isEmpty() ? "<empty>" : sValue), clazz, field);
                            }
    
                            setFieldValue(stats, field, newObject, array, clazz);
                        }
                        else if (value instanceof String)
                        {
                            String sValue = normalizeInputIfNecesary((String) value, field, clazz);

                            Object[] array = (Object[]) Array.newInstance(field.getType().getComponentType(), 1);
                                                                
                            Object[] constants = field.getType().getComponentType().getEnumConstants();

                            boolean found = false;

                            for (Object o : constants) {

                            	try 
                            	{
                            		String[] aliases = (String[]) o.getClass().getMethod("aliases").invoke(o);	
                            		
                            		for (String alias : aliases)
                            		{
                            			if (alias.equals(sValue))
                            			{
                                            array[0] = o;
                                            found = true;
                                            break;
                                        }
                            		}
                            	}
                            	catch(NoSuchMethodException e)
                            	{
                                    if (o.toString().equals(sValue))
                                    {
                                        array[0] = o;
                                        found = true;
                                        break;
                                    }
                            	}
                            }

                            if (!found)
                                throw new XObjectException(
                                        "Invalid enum value: " + (sValue.isEmpty() ? "<empty>" : sValue), clazz);

                            setFieldValue(stats, field, newObject, array, clazz);
                        }
                        else
                        {
                            throw new XObjectException("Required JSONArray field", clazz, field);
                        }
					} else if (field.isAnnotationPresent(Reference.class)) {
						if (!(value instanceof String))
							throw new XObjectException("Required String field", clazz, field);

						createReference(stats, newObject, field,
								normalizeInputIfNecesary(value.toString(), field, clazz), clazz);
					} else {
						if (value instanceof JSONArray) {
    						JSONArray jsonArray = (JSONArray) value;
    
    						Object[] array = (Object[]) Array.newInstance(field.getType().getComponentType(),
    								jsonArray.length());
    
    						for (int i = 0; i < array.length; i++)
    							array[i] = _parseJSON(jsonArray.getJSONObject(i), field.getType().getComponentType(),
    									stats);
    
    						setFieldValue(stats, field, newObject, array, clazz);
						} else if (value instanceof JSONObject) {
						    Object[] array = (Object[]) Array.newInstance(field.getType().getComponentType(),
                                    1);
    
                            for (int i = 0; i < array.length; i++)
                                array[i] = _parseJSON((JSONObject) value, field.getType().getComponentType(),
                                        stats);
                                        
                            setFieldValue(stats, field, newObject, array, clazz);
						} else if (!field.isAnnotationPresent(Optional.class)) {
						    throw new XObjectException("Required array value", clazz, field);
						}
						    
					}
				}
			} catch (JSONException | IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new XObjectException(e, clazz, field);
			}
		}

		PostCheckType checkTypeAnnotation = clazz.getAnnotation(PostCheckType.class);

		if (checkTypeAnnotation != null) {
			Class<?> checkTypeClass = checkTypeAnnotation.value();

			try {
				Method method = checkTypeClass.getMethod("checkType", clazz);

				Boolean returnedValue = (Boolean) method.invoke(null, newObject);

				if (!returnedValue.booleanValue())
					throw new XObjectException(
							"Invalid value " + newObject + " after checking type using " + checkTypeClass, clazz);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				throw new XObjectException(e, clazz);
			}
		}

		if (!foundAttributes.isEmpty()) {
			throw new XObjectException("Attributes were not found " + foundAttributes, clazz);
		}

		return newObject;
	}

	private JSONObject _toJSON(Object object, XObjectStats stats, boolean transformXML) throws XObjectException {
		JSONObject jsonObject = new JSONObject();

		Class<?> clazz = object.getClass();

		if (isAnnotationPresent(clazz, Identified.class))
			jsonObject.put(ID_CONSTANT, stats.createIdentifier(object));

		if (isAnnotationPresent(clazz, ExportClassName.class))
		{
			String className = object.getClass().getName();
			
			String classNamePrefix = clazz.getAnnotation(ExportClassName.class).value();
			
			if (!className.startsWith(classNamePrefix))
				throw new XObjectException("Class " + className + " do not have prefix " + classNamePrefix, clazz);
			
			className = className.substring(classNamePrefix.length());
			
			jsonObject.put(CLASS_NAME_CONSTANT, className);
		}

		for (Field field : getClassHierarchyFields(clazz)) {
			try {
				field.setAccessible(true);

				String fieldName = field.getName();

				if (field.isAnnotationPresent(Name.class))
					fieldName = field.getAnnotation(Name.class).value();

				Object subObject = field.get(object);

				if (field.isAnnotationPresent(SkipExportation.class)) {
					// Do nothing
				} else if (subObject == null) {
					if (!field.isAnnotationPresent(Optional.class))
						throw new XObjectException("Required field", clazz, field);
				} else if (field.getType().equals(String.class) || field.getType().equals(int.class)
						|| field.getType().equals(Integer.class) || field.getType().equals(long.class)
						|| field.getType().equals(Long.class) || field.getType().equals(double.class)
						|| field.getType().equals(Double.class) || field.getType().equals(float.class)
						|| field.getType().equals(Float.class) || field.getType().equals(boolean.class)
						|| field.getType().equals(Boolean.class) || field.getType().isEnum()) {
					if (field.isAnnotationPresent(Reference.class))
						throw new XObjectException("Unexpected @Reference annotation", clazz, field);

					jsonObject.put(fieldName, subObject.toString());
				} else if (!field.getType().isArray()) {
					if (field.isAnnotationPresent(Reference.class))
						jsonObject.put(fieldName, stats.createIdentifier(subObject));
					else if (field.isAnnotationPresent(ExportAsString.class))
					    jsonObject.put(fieldName, subObject.toString());
					else
						jsonObject.put(fieldName, _toJSON(subObject, stats, transformXML));
				} else if (field.getType().getComponentType() == String.class) {
					if (field.isAnnotationPresent(Reference.class))
						throw new XObjectException("Unexpected @Reference annotation", clazz, field);

					String[] subObjects = (String[]) field.get(object);

					JSONArray jsonArray = new JSONArray();

					for (int i = 0; i < subObjects.length; i++)
						jsonArray.put(i, subObjects[i]);

					jsonObject.put(fieldName, jsonArray);
				} else if (field.getType().getComponentType() == int.class) {
					if (field.isAnnotationPresent(Reference.class))
						throw new XObjectException("Unexpected @Reference annotation", clazz, field);

					int[] subObjects = (int[]) field.get(object);

					JSONArray jsonArray = new JSONArray();

					for (int i = 0; i < subObjects.length; i++)
						jsonArray.put(i, subObjects[i]);

					jsonObject.put(fieldName, jsonArray);
				} else if (field.getType().getComponentType() == long.class) {
					if (field.isAnnotationPresent(Reference.class))
						throw new XObjectException("Unexpected @Reference annotation", clazz, field);

					long[] subObjects = (long[]) field.get(object);

					JSONArray jsonArray = new JSONArray();

					for (int i = 0; i < subObjects.length; i++)
						jsonArray.put(i, subObjects[i]);

					jsonObject.put(fieldName, jsonArray);
				} else if (field.getType().getComponentType() == float.class) {
					if (field.isAnnotationPresent(Reference.class))
						throw new XObjectException("Unexpected @Reference annotation", clazz, field);

					float[] subObjects = (float[]) field.get(object);

					JSONArray jsonArray = new JSONArray();

					for (int i = 0; i < subObjects.length; i++)
						jsonArray.put(i, subObjects[i]);

					jsonObject.put(fieldName, jsonArray);
				} else if (field.getType().getComponentType() == double.class) {
					if (field.isAnnotationPresent(Reference.class))
						throw new XObjectException("Unexpected @Reference annotation", clazz, field);

					double[] subObjects = (double[]) field.get(object);

					JSONArray jsonArray = new JSONArray();

					for (int i = 0; i < subObjects.length; i++)
						jsonArray.put(i, subObjects[i]);

					jsonObject.put(fieldName, jsonArray);
				} else if (field.getType().getComponentType() == boolean.class) {
					if (field.isAnnotationPresent(Reference.class))
						throw new XObjectException("Unexpected @Reference annotation", clazz, field);

					boolean[] subObjects = (boolean[]) field.get(object);

					JSONArray jsonArray = new JSONArray();

					for (int i = 0; i < subObjects.length; i++)
						jsonArray.put(i, subObjects[i]);

					jsonObject.put(fieldName, jsonArray);
                } else if (field.getType().getComponentType().isEnum()) {
                    if (field.isAnnotationPresent(Reference.class))
                        throw new XObjectException("Unexpected @Reference annotation", clazz, field);

                    Object[] subObjects = (Object[]) field.get(object);

                    JSONArray jsonArray = new JSONArray();

                    for (int i = 0; i < subObjects.length; i++)
                        jsonArray.put(i, subObjects[i].toString());

                    jsonObject.put(fieldName, jsonArray);
				} else {
					Object[] subObjects = (Object[]) field.get(object);

					if (field.isAnnotationPresent(Reference.class)) {
						String[] identifiers = new String[subObjects.length];

						for (int i = 0; i < subObjects.length; i++)
							identifiers[i] = stats.createIdentifier(subObjects[i]);

						jsonObject.put(fieldName, String_join(",", identifiers));
					} else {
						JSONArray jsonArray = new JSONArray();

						for (int i = 0; i < subObjects.length; i++)
							jsonArray.put(i, _toJSON(subObjects[i], stats, transformXML));

						jsonObject.put(fieldName, jsonArray);
					}
				}
			} catch (IllegalAccessException e) {
				throw new XObjectException(e, clazz, field);
			}
		}

		return jsonObject;
	}

	/// Return the list of fields in \p clazz or any of its ancestors
	static public ArrayList<Field> getClassHierarchyFields(Class<?> clazz) {
		ArrayList<Field> fields = new ArrayList<>();

		while (clazz != Object.class) {
			for (Field field : clazz.getDeclaredFields())
				if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
						&& !field.isSynthetic())
					fields.add(field);

			clazz = clazz.getSuperclass();
		}

		return fields;
	}

	/// Look for an annotation in \p clazz or any of its ancestors
	private boolean isAnnotationPresent(Class<?> clazz, Class<? extends Annotation> annotation) {
		while (clazz != Object.class) {
			if (clazz.isAnnotationPresent(annotation)) {
				return true;
			}

			clazz = clazz.getSuperclass();
		}

		return false;
	}

	public static JSONObject toJSON(Object object, boolean transformXML) throws XObjectException {
		return new XObject()._toJSON(object, new XObjectStats(), transformXML);
	}

	public static void toJSON(Object object, OutputStream outputStream, int indentFactor, boolean transformXML)
			throws XObjectException, IOException {
		try (OutputStreamWriter os = new OutputStreamWriter(outputStream, "UTF-8")) {
			JSONObject o = toJSON(object, transformXML);

			o.write(os, indentFactor, indentFactor);
		} catch (UnsupportedEncodingException e) {
			throw new XObjectException(e, object.getClass());
		}
	}

	public static void toJSON(Object object, File file, int indentFactor, boolean transformXML)
			throws IOException, XObjectException {
		FileOutputStream writer = new FileOutputStream(file);

		toJSON(object, writer, indentFactor, transformXML);

		writer.close();
	}

	private Object createObjectFromString(XObjectStats stats, Class<?> fieldClass, String value, Class<?> clazz)
			throws XObjectException, InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		
		if (value == null)
			return null;
		
		return fieldClass.getConstructor(String.class).newInstance(value);
	}

	private void createReference(XObjectStats stats, Object newObject, Field field, String value, Class<?> clazz)
			throws XObjectException {
		InternalReference reference = new InternalReference(newObject, field, value);

		XObjectException e = solveReference(stats, reference, clazz);

		if (e != null)
			this.references.add(reference);
	}

	private XObjectException solveReferences(XObjectStats stats, Class<?> clazz) throws XObjectException {
		for (InternalReference reference : this.references) {
			XObjectException e = solveReference(stats, reference, clazz);

			if (e != null)
				throw e;
		}

		return null;
	}

	private XObjectException solveReference(XObjectStats stats, InternalReference reference, Class<?> clazz)
			throws XObjectException {
		try {
			Object value = null;

			XObjectException exception = null;

			if (reference.field.getType().isArray()) {
				String[] identifiers = reference.id.split(",");

				Object[] objects = (Object[]) Array.newInstance(reference.field.getType().getComponentType(),
						identifiers.length);

				for (int i = 0; i < identifiers.length; i++) {
					String id = identifiers[i];

					Object object = stats.findReferencedObject(id, reference.field.getType().getComponentType(),
							reference.field.getAnnotation(MultiReference.class));

					if (object == null) {
						Class<?> otherClass = stats.lostIdentifiedObjects.get(id);

						if (otherClass != null)
							exception = new XObjectException("Could not find reference: " + id + ", do you wanna mean "
									+ otherClass.getName() + "?", reference.object.getClass(), reference.field);
						else
							exception = new XObjectException("Could not find reference: " + id,
									reference.object.getClass(), reference.field);
					}

					objects[i] = object;
				}

				value = normalizeArray(objects, reference.field, clazz);
			} else {
				Object object = stats.findReferencedObject(reference.id, reference.field.getType(),
						reference.field.getAnnotation(MultiReference.class));

				if (object == null) {
					object = stats.lostIdentifiedObjects.get(reference.id);

					if (object != null)
						exception = new XObjectException(
								"Could not find reference: " + reference.id + ", do you wanna mean " + object + "?",
								reference.object.getClass(), reference.field);
					else
						exception = new XObjectException("Could not find reference: " + reference.id,
								reference.object.getClass(), reference.field);
				}

				value = object;
			}

			if (exception != null)
				return exception;

			else if (value != null) {
				setFieldValue(stats, reference.field, reference.object, value, clazz);

				return null;
			} else {
				return null;
			}
		} catch (IllegalArgumentException | IllegalAccessException | XObjectException e) {
			throw new XObjectException(e, reference.object.getClass(), reference.field);
		}
	}

	static public String toString(Object o, XObjectStats stats) {
		try {
			if (o == null)
				return "null";

			if (o.getClass().equals(String.class))
				return "\"" + o.toString().trim() + "\"";

			if (o.getClass().isArray()) {
				String s = o.getClass().getComponentType().getSimpleName() + "{" + ((Object[]) o).length + "}[";

				boolean first = true;

				for (Object oElem : (Object[]) o) {
					if (s.length() > 200) {
						s += "...";
						break;
					}

					if (first)
						first = false;
					else
						s += ",";

					s += toString(oElem, stats);
				}

				return s + "]";
			}

			String s = o.getClass().getSimpleName();

			String id = stats.identifiedObjects.getKey(o);

			boolean closeBracket = false;

			if (id != null) {
				s += "{ID=\"" + id + "\"";
				closeBracket = true;
			}

			for (Field field : XObject.getClassHierarchyFields(o.getClass())) {
				field.setAccessible(true);

				String fieldName = field.getName();

				if (field.isAnnotationPresent(Name.class))
					fieldName = field.getAnnotation(Name.class).value();

				if (field.isAnnotationPresent(DescriptiveMember.class)) {
					if (!closeBracket)
						s += "{" + fieldName + "=" + toString(field.get(o), stats);
					else
						s += "," + fieldName + "=" + toString(field.get(o), stats);

					closeBracket = true;
				}
			}

			if (closeBracket)
				return s + "}";

			return o.toString();
		} catch (IllegalArgumentException | SecurityException | IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	static public String String_join(String delimiter, String[] array) {
		String s = null;

		for (String i : array) {
			if (s == null)
				s = i;
			else
				s += delimiter + i;
		}

		return s;
	}
}
