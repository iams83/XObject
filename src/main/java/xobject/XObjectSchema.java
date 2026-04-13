package xobject;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;

import xobject.XObject.Comment;
import xobject.XObject.TrailingComment;

public class XObjectSchema extends XObjectSchemaParser
{
	public enum SchemaBasicType
	{
		XSDString  (String.class,    "string",  "string"),
		XSDBoolean (boolean.class,   "boolean", "boolean"),
		XSDChar    (char.class,      "byte",    "byte"),
		XSDInteger (int.class,       "decimal", "integer"),
		XSDLong    (long.class,      "long",    "long integer"),
		XSDFloat   (float.class,     "float",   "simple precision floating point number"),
		XSDDouble  (double.class,    "double",  "double precision floating point number"),
		XSDBooleanC(Boolean.class,   "boolean", "boolean"),
		XSDCharC   (Character.class, "byte",    "byte"),
		XSDIntegerC(Integer.class,   "decimal", "integer"),
		XSDLongC   (Long.class,      "long",    "long integer"),
		XSDFloatC  (Float.class,     "float",   "simple precision floating point number"),
		XSDDoubleC (Double.class,    "double",  "double precision floating point number");
		
		final private Class<?> clazz;
		
		final String name, xsdName;

		SchemaBasicType(Class<?> clazz, String name, String humanReadableName)
		{
			this.clazz = clazz;
			this.xsdName = "xs:" + name;
			this.name= humanReadableName;
		}
		
		static SchemaBasicType fromClass(Class<?> clazz)
		{
			for (SchemaBasicType type : values())
			{
				if (type.clazz == clazz)
					return type;
			}
			
			return null;
		}
	}
	
	abstract static public class XMLBase
	{
		static int counter = 0;
		
		final public int order = counter ++;
		
		private String[] commentLines;

		public void addComment(Comment comment)
		{
			if (comment != null)
				this.commentLines = comment.value().split("\n");
		}

		public void addComment(String[] comment)
		{
			this.commentLines = comment;
		}
		
		public String commentAsSentences()
		{
			return XObjectSchema.commentAsSentences(this.commentLines);
		}
		
		abstract public void printToXSDImpl(PrintStream stream, String indent);

		final public void printToXSD(PrintStream stream, String indent)
		{
			if (this.commentLines != null)
			{
				if (this.commentLines.length == 1)
				{
					stream.println(indent + "<!-- " + this.commentLines[0] + " -->");
				}
				else
				{
					stream.println(indent + "<!--");
					
					for (String s : this.commentLines)
						stream.println(indent + "     " + s);
					
					stream.println(indent + "-->");
				}
			}
			
			this.printToXSDImpl(stream, indent);
		}

		abstract public void printToDoc(PrintStream stream);
	}
	
	public static class XMLElement extends XMLBase
	{
		public enum Type
		{
			Sequence, All, Any
		}
		
		final private String name;

		private String[] trailingCommentLines;

		private boolean optionalDefined = false;
		private boolean optional = false;
		
		private boolean unboundedDefined = false;
		private boolean unbounded = false;
		
		private Type type = null;

		private XMLElement parent;
		
		final private ArrayList<XMLBase> innerElements = new ArrayList<>();
		
		final private ArrayList<XMLAttribute> innerAttributes = new ArrayList<>();

		public XMLElement(String elementName)
		{
			this.name = elementName;
		}

		public void addTrailingComment(TrailingComment comment)
		{
			if (comment != null)
				this.trailingCommentLines = comment.value().split("\n");
		}

		public void setType(Type type)
		{
			this.type = type;
		}

		public void setOptional(boolean optional)
		{
			this.optionalDefined = true;
			this.optional = optional;					
		}

		public void setUnbounded(boolean unbounded)
		{
			this.unboundedDefined = true;
			this.unbounded = unbounded;					
		}
		
		public void addElement(XMLElementRef ref)
		{
			this.innerElements.add(ref);
		}

		public void addElement(XMLElement ref)
		{
			ref.setChildElement(this);
			
			this.innerElements.add(ref);
		}

		private void setChildElement(XMLElement xmlElement)
		{
			this.parent = xmlElement; 
		}

		public void addAttribute(XMLAttribute ref)
		{
			this.innerAttributes.add(ref);
		}

		public void printToXSDImpl(PrintStream stream, String indent)
		{
        	stream.print(indent + "<xs:element");

        	if (this.unboundedDefined)
        	{
	            if (this.unbounded)
	            	stream.print(" maxOccurs=\"unbounded\"");
	            else
	            	stream.print(" maxOccurs=\"1\"");
        	}
        	
        	if (this.optionalDefined)
        	{
	            if (this.optional)
	            	stream.print(" minOccurs=\"0\"");
	            else
	            	stream.print(" minOccurs=\"1\"");
        	}
        	
        	stream.println(" name=\"" + this.name + "\">");

        	stream.println(indent + "    <xs:complexType>");

        	Type selectedType = this.type; 
        	
        	if (selectedType == null)
        	{
		        if (this.innerElements.size() == 1)
		        	selectedType = XMLElement.Type.Sequence;
		        else if (this.innerElements.size() > 1)
		        	selectedType = XMLElement.Type.All;
        	}
        	
        	if (selectedType != null)
        		stream.println(indent + "    <xs:" + selectedType.name().toLowerCase() + ">");
        	
        	for (XMLBase innerElement : this.innerElements)
        		innerElement.printToXSD(stream, indent + "        ");
        	
        	if (selectedType != null)
        		stream.println(indent + "    </xs:" + selectedType.name().toLowerCase() + ">");

        	for (XMLBase innerAttribute : this.innerAttributes)
        		innerAttribute.printToXSD(stream, indent + "        ");
        	
            stream.println(indent + "    </xs:complexType>");
            stream.println(indent + "</xs:element>");
            stream.println(indent + "");
		}
		
		@Override
		public void printToDoc(PrintStream stream)
		{
			stream.print("<aX id=\"" + (this.parent != null ? this.parent.getAncestorName() + "_" : "") + this.name + "\"><h2><code>" + this.name + "</code></a>");
			
			if (this.parent != null)
				stream.print(" (child of <code>" + this.parent.name + "</code> described above)");
			
			stream.println("</h2>");
			stream.println();
			
			if (!this.commentAsSentences().isEmpty())
				stream.print("<p>Description: " + this.commentAsSentences() + "</p>");
			
			
			if (!this.innerElements.isEmpty())
			{
				stream.println("<p>" + this.name + " element contains the following XML child elements:</p>");
				
				stream.println("<ul>");
				
				for (XMLBase element : this.innerElements)
				{
					if (element instanceof XMLElement)
					{
						XMLElement wholeElement = (XMLElement) element;
						
						XMLElementRef dummyRef = new XMLElementRef();
						dummyRef.setRef(wholeElement.name);
						dummyRef.setParent(this);
						dummyRef.addComment(new String[] { wholeElement.commentAsSentences() });
						dummyRef.setOptional(dummyRef.optional);
						dummyRef.setUnbounded(dummyRef.unbounded);
						
						dummyRef.printToDoc(stream);
					}
					else
					{
						element.printToDoc(stream);
					}						
				}

				stream.println("</ul>");
			}
			
			if (!this.innerAttributes.isEmpty())
			{
				if (this.innerElements.isEmpty())
					stream.println("<p>" + this.name + " element has no child elements but the following XML attributes:</p>");
				else
					stream.println("<p>" + this.name + " element also contains the following XML attributes:</p>");
				
				stream.println("<ul>");
				
				for (XMLAttribute attribute : this.innerAttributes)
					attribute.printToDoc(stream);
	
				stream.println("</ul>");
			}
			

			if (!this.trailingCommentAsSentences().isEmpty())
				stream.print("<p>" + this.trailingCommentAsSentences() + "</p>");
			

			if (!this.innerElements.isEmpty())
			{
				for (XMLBase element : this.innerElements)
				{
					if (element instanceof XMLElement)
						element.printToDoc(stream);
				}
			}
		}

		private String trailingCommentAsSentences()
		{
			return XObjectSchema.commentAsSentences(this.trailingCommentLines);
		}

		public int elementsCount()
		{
			return this.innerElements.size();
		}

		public boolean isEmpty()
		{
			return this.innerAttributes.isEmpty() && this.innerElements.isEmpty();
		}

		public String getAncestorName()
		{
			if (this.parent != null)
				return this.parent.getAncestorName() + "_" + this.name;
			else
				return this.name;
		}
	}
	
	public static class XMLElementRef extends XMLBase
	{
		public String name, ref;
		
		private SchemaBasicType basicType;
		
		private boolean optional = false;
		private boolean unbounded = false;

		private XMLElement parent;

		public XMLElementRef()
		{
		}
		
		public void setParent(XMLElement xmlElement)
		{
			this.parent = xmlElement;
		}

		public void setName(String name, SchemaBasicType typeName)
		{
			this.name = name;
			this.basicType = typeName;
		}

		public void setRef(String ref)
		{
			this.ref = ref;
		}
		
		public void setOptional(boolean optional)
		{
			this.optional = optional;					
		}

		public void setUnbounded(boolean unbounded)
		{
			this.unbounded = unbounded;					
		}

		@Override
		public void printToXSDImpl(PrintStream stream, String indent)
		{
        	stream.print(indent + "<xs:element");

            if (this.unbounded)
            	stream.print(" maxOccurs=\"unbounded\"");
            else
            	stream.print(" maxOccurs=\"1\"");

            if (this.optional)
            	stream.print(" minOccurs=\"0\"");
            else
            	stream.print(" minOccurs=\"1\"");

        	if (this.name != null)
        		stream.print(" name=\"" + this.name + "\"");

        	if (this.basicType != null)
        		stream.print(" type=\"" + this.basicType.xsdName + "\"");

        	if (this.ref != null)
        		stream.print(" ref=\"" + this.ref + "\"");

        	stream.println("/>");
		}
		
		@Override
		public void printToDoc(PrintStream stream)
		{
			stream.println("<li>");
			
			if (this.unbounded)
			{
				if (this.optional)
					stream.print("Zero or more ");
				else 
					stream.print("A set of ");
			}
			else if (this.optional)
				stream.print("Zero or one ");
			
			if (this.ref != null)
			{
				stream.print("<aX href=\"#" + (this.parent != null ? this.parent.getAncestorName() + "_" : "") + this.ref + "\"><code>" + this.ref + "</code></a>");
				
				stream.print(this.unbounded || this.optional ? " elements" : "");
				
				if (this.parent != null)
					stream.print(" (See specific <code>" + this.ref + "</code> element child of <code>" + this.parent.name + ")</code>");

				if (!this.commentAsSentences().isEmpty())
					stream.println(": " + this.commentAsSentences());
				else
					stream.println(".");
			}
			else
			{
				String comment = this.commentAsSentences();
				
				if (this.basicType != null && this.basicType != SchemaBasicType.XSDString)
					comment += "Data type: <code>" + (this.basicType.name) + "</code>. ";
				
				comment = comment.trim();
				
				stream.println("<code>" + this.name + "</code>" + (comment.isEmpty() ? "." : ": " + comment));
			}
			
			stream.println("</li>");
		}
	}
	
	static public class XMLAttribute extends XMLBase
	{
		final public String name;
		private SchemaBasicType basicType;
		private String elementType;
		final public boolean required;
		
		public XMLAttribute(String name, boolean required)
		{
			this.name = name;
			this.required = required;
		}

		public void setElementType(String type)
		{
			this.elementType = type;
		}
		
		public void setBasicType(SchemaBasicType type)
		{
			this.basicType = type;
		}
		
		@Override
		public void printToXSDImpl(PrintStream stream, String indent)
		{
        	stream.print(indent + "<xs:attribute name=\"" + this.name + "\" type=\"" + 
        			(this.basicType != null ? this.basicType.xsdName : 
        				(this.elementType != null ? this.elementType : SchemaBasicType.XSDString.xsdName)) + "\"");
            
            if (this.required)
            	stream.print(" use=\"required\"");
            
            stream.println("/>");
		}
		
		@Override
		public void printToDoc(PrintStream stream)
		{
			stream.println("<li>");
			
			String comment = this.commentAsSentences();

			if ((this.basicType != null || this.elementType != null) && this.basicType != SchemaBasicType.XSDString)
			{	
				comment += "Data type: <code>" + 
						(this.basicType != null ? this.basicType.name : "<aX href=\"#" + this.elementType + "\">" + this.elementType + "</a>") + 
						"</code>. ";
			}
			
			if (!this.required)
				comment += "Optional. ";
			
			comment = comment.trim();
			
			stream.println("<code>" + this.name + "</code>" + (comment.isEmpty() ? "." : ": " + comment));

			stream.println("</li>");
		}
	}
	
	static abstract class XMLRestriction
	{
		abstract public void printToStream(PrintStream stream, String indent);

		abstract public void printToDoc(PrintStream stream);
	}
	
	public static class XMLBuiltinData extends XMLRestriction
	{
		final public SchemaBasicType baseType;

		public XMLBuiltinData(SchemaBasicType baseType)
		{
			this.baseType = baseType;
		}
		
		@Override
		public void printToStream(PrintStream stream, String indent)
		{
			stream.println(indent + "<xs:restriction base=\"" + this.baseType.xsdName + "\">");
		}

		@Override
		public void printToDoc(PrintStream stream)
		{
			if (!this.baseType.equals(SchemaBasicType.XSDString))
				stream.print("This attribute is represented as a <b>" + this.baseType.name + "</b>. ");
		}
	}
	
	public static class XMLEnum extends XMLRestriction
	{
		class XMLValue
		{
			final public String constant, comment;
			
			public XMLValue(String value, String comment)
			{
				this.constant = value;
				this.comment = comment;
			}
		}
		
		private boolean emptiable;
		
		final private ArrayList<XMLValue> values = new ArrayList<>();

		public XMLEnum(boolean emptiable)
		{
			this.emptiable = emptiable;
		}
		
		public void addValue(String value, String comment)
		{
			this.values.add(new XMLValue(value, comment));
		}

		@Override
		public void printToStream(PrintStream stream, String indent)
		{
			stream.println(indent + "<xs:restriction base=\"xs:token\">");
			
			if (this.emptiable)
				stream.println(indent + "    <xs:enumeration value=\"\"/>");
			
			for (XMLValue value : this.values)
			{
				stream.print(indent + "    <xs:enumeration value=\"" + escapeXMLCharacterEntities(value.constant) + "\"/>");
				
				if (value.comment != null)
					stream.print("<!-- " + value.comment + " -->");
				
				stream.println();
			}
		}

		@Override
		public void printToDoc(PrintStream stream)
		{
			stream.println("<p>This type may take one of the following values" + (this.emptiable ? " (or left blank)" : "") + ":</p>");
			
			stream.println("<ul>");
			
			for (XMLValue value : this.values)
			{
				String comment = value.comment != null ? commentAsSentences(new String[] { value.comment }) : "";
				
				stream.println("<li><code>" + value.constant + "</code>" + (comment.isEmpty() ? "." : ": " + comment) + "</li>");
			}
			
			stream.println("</ul>");
			
			stream.println();
		}

		public void setEmptiable()
		{
			this.emptiable = true;
		}
	}

	static class XMLSimpleType extends XMLBase
	{
		static class XMLConstraint
		{
			final public String name, value, docPredicate;
			
			public XMLConstraint(String constraint, String value, String docPredicate)
			{
				this.name = constraint;
				this.value = value;
				this.docPredicate = docPredicate;
			}
		}
		
		final public String name;
		
		final public XMLRestriction restriction;
		
		final public ArrayList<XMLConstraint> constraints = new ArrayList<>();

		public XMLSimpleType(String name, XMLRestriction restriction)
		{
			this.name = name;
			
			this.restriction = restriction;
		}
		
		@Override
		public void printToXSDImpl(PrintStream stream, String indent)
		{
			stream.println(indent + "<xs:simpleType name=\"" + this.name + "\">");

			this.restriction.printToStream(stream, indent + "    ");

			for (XMLConstraint constraint : this.constraints)
				stream.println(indent + "        <xs:" + constraint.name + " value=\"" + escapeXMLCharacterEntities(constraint.value) + "\"/>");

			stream.println(indent + "    </xs:restriction>");

			stream.println(indent + "</xs:simpleType>");
			stream.println();
		}

		public void addConstraint(String constraint, String value, String docPredicate)
		{
			this.constraints.add(new XMLConstraint(constraint, value, docPredicate));
		}
		
		@Override
		public void printToDoc(PrintStream stream)
		{
			stream.println("<aX id=\"" + this.name + "\"><h2><code>" + this.name + "</code></h2></a>");
			
			stream.println();
			
			if (!this.commentAsSentences().isEmpty())
				stream.print("<p>Description: " + this.commentAsSentences() + "</p>");
			
			this.restriction.printToDoc(stream);
			
			if (!this.constraints.isEmpty())
			{
				for (XMLConstraint constraint : this.constraints)
					stream.print(constraint.docPredicate.replace("{0}", constraint.value));

				stream.println();
			}
			
			stream.println();
		}
	}
	
	final private ArrayList<XMLBase> elements = new ArrayList<>();
	
	public void add(XMLBase xmlObject)
	{
		this.elements.add(xmlObject);
	}
	
	public void printToXSD(OutputStream ostream)
	{
        PrintStream stream = new PrintStream(ostream);
		
		stream.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n" + 
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\">");

		for (XMLBase element : elements)
			element.printToXSD(stream, "");

        stream.print("</xs:schema>");
	}

	public void printToDoc(OutputStream ostream)
	{
		PrintStream stream = new PrintStream(ostream);
		
		String title = ((XMLElement) this.elements.get(this.elements.size() - 1)).name;
		
		stream.println("<h1>" + title + "</h1>");

		TreeSet<XMLBase> sortedElements = new TreeSet<XMLBase>(new Comparator<XMLBase>()
		{
			@Override
			public int compare(XMLBase o1, XMLBase o2)
			{
				Class<? extends XMLBase> o1Type = o1.getClass();
				Class<? extends XMLBase> o2Type = o2.getClass();
				
				int classLexOrder = o1Type.getName().compareTo(o2Type.getName());
				
				if (classLexOrder != 0)
					return -classLexOrder;
				
				if (o1Type.equals(XMLSimpleType.class))
				{
					XMLSimpleType o1Simple = (XMLSimpleType) o1;
					XMLSimpleType o2Simple = (XMLSimpleType) o2;
					
					classLexOrder = o1Simple.restriction.getClass().getName().compareTo(
							o2Simple.restriction.getClass().getName());
					
					if (classLexOrder != 0)
						return classLexOrder;
					
					classLexOrder = o1Simple.name.compareTo(o2Simple.name);
					
					if (classLexOrder != 0)
						return classLexOrder;
				}
				
				return o1.order - o2.order;
			}
		});
		
		sortedElements.addAll(this.elements);
		
		stream.println("<h1>Simple types</h1>");
		
		for (XMLBase element : sortedElements)
		{
			if (element instanceof XMLSimpleType)
			{
				XMLSimpleType simple = (XMLSimpleType) element;
				
				if (!(simple.restriction instanceof XMLEnum))
					element.printToDoc(stream);
			}
		}
		
		stream.println("<h1>Enumerations</h1>");

		for (XMLBase element : sortedElements)
		{
			if (element instanceof XMLSimpleType)
			{
				XMLSimpleType simple = (XMLSimpleType) element;
				
				if (simple.restriction instanceof XMLEnum)
					element.printToDoc(stream);
			}
		}

		stream.println("<h1>Elements</h1>");
		
		for (XMLBase element : sortedElements)
		{
			if (!(element instanceof XMLSimpleType))
				element.printToDoc(stream);
		}
	}

	private static String escapeXMLCharacterEntities(String constant)
	{
		return constant	
				.replaceAll(">", "&gt;")
				.replaceAll("<", "&lt;")
				.replaceAll("\"", "&quot;");
	}


	static public String commentAsSentences(String[] commentLines)
	{
		if (commentLines == null)
			return "";
		
		String comment = String.join(" ", commentLines).trim().trim();
		
		if (comment.endsWith("."))
			comment += " ";
		
		else if (!comment.endsWith(". "))
			comment += ". ";
		
		return comment;
	}

}
