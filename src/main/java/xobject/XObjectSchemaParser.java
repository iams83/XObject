package xobject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.TreeSet;

import xobject.XObject.CheckType;
import xobject.XObject.CollapseArray;
import xobject.XObject.Comment;
import xobject.XObject.Emptiable;
import xobject.XObject.EnumComment;
import xobject.XObject.Identified;
import xobject.XObject.Optional;
import xobject.XObject.OptionallyIdentified;
import xobject.XObject.Reference;
import xobject.XObject.XSDFilter;
import xobject.XObject.TextNode;
import xobject.XObject.TrailingComment;
import xobject.XObject.XSDEnumFilter;
import xobject.XObject.XSDNestElement;
import xobject.XObjectSchema.XMLAttribute;
import xobject.XObjectSchema.XMLBase;
import xobject.XObjectSchema.XMLElement;
import xobject.XObjectSchema.XMLElementRef;
import xobject.XObjectSchema.XMLEnum;
import xobject.XObjectSchema.XMLRestriction;
import xobject.XObjectSchema.XMLSimpleType;
import xobject.XObjectSchema.SchemaBasicType;
import xobject.XObjectSchema.XMLBuiltinData;

public class XObjectSchemaParser
{
    public static XObjectSchema createXSDSchema(Class<?> clazz, String rootElementName, String[] includeXSDData)
    {
    	XObjectSchema doc = new XObjectSchema();
        
    	TreeMap<String, XMLBase> classes = new TreeMap<String, XMLBase>();
        
        defineClass(rootElementName, clazz, false, doc, classes, new TreeSet<String>(Arrays.asList(includeXSDData)));

        return doc;
    }

    private static XMLElement defineClass(String elementName, Class<?> clazz, boolean hasParentElement, XObjectSchema document, TreeMap<String, XMLBase> classes, TreeSet<String> includeXSDData)
    {
        if (!hasParentElement && classes.containsKey(elementName))
    		return null;
            
        XMLElement finalElement = new XMLElement(elementName);
        
		classes.put(elementName, finalElement);
		
    	if (clazz != null)
    	{
    		finalElement.addComment(clazz.getAnnotation(Comment.class));
    		finalElement.addTrailingComment(clazz.getAnnotation(TrailingComment.class));
    	}
    	
        ArrayList<Field> fieldElements = new ArrayList<Field>();
        
        ArrayList<Field> fieldAttributes = new ArrayList<Field>();
        
        for (Field f : XObject.getClassHierarchyFields(clazz))
        {
    		XSDFilter skipXSDDeclaration = f.getAnnotation(XSDFilter.class);
    		
    		if (skipXSDDeclaration != null)
    		{
    			if (!includeXSDData.contains(skipXSDDeclaration.value()))
    				continue;
    		}
    		
            Class<?> type = f.getType();
            
            if (f.isAnnotationPresent(TextNode.class))
            {
            	SchemaBasicType typeName = SchemaBasicType.fromClass(f.getType());
	        	
	        	XMLElementRef elementRef = new XMLElementRef();
	        	
        		elementRef.addComment(f.getType().getAnnotation(Comment.class));
	        	elementRef.setOptional(f.isAnnotationPresent(Optional.class));
	        	elementRef.setName(f.getName(), typeName);

                if (f.getType().isArray() && !f.isAnnotationPresent(CollapseArray.class))
                {
                	elementRef.setUnbounded(true);
                	finalElement.setType(XMLElement.Type.Sequence);
                }
                else
                	elementRef.setUnbounded(false);
                
                finalElement.addElement(elementRef);
            }

            else if (f.getType().isEnum() ||
            		f.isAnnotationPresent(CheckType.class) ||
            		f.isAnnotationPresent(Reference.class))
			{
        		fieldAttributes.add(f);
			}
        	else if (f.isAnnotationPresent(XSDNestElement.class))
        	{
        		fieldElements.add(f);
        	}
        	else if (type.isArray())
        	{
        		CollapseArray collapseArrayAnnotation = f.getAnnotation(CollapseArray.class);
            	
            	if (collapseArrayAnnotation != null)
            	{
            		String nestedClassName = collapseArrayAnnotation.value();
            		
            		defineClass(nestedClassName, type.getComponentType(), false, document, classes, includeXSDData);
            		
                	XMLElementRef elementRef = new XMLElementRef();
                	elementRef.setRef(nestedClassName);
                	elementRef.setOptional(true);
                	elementRef.setUnbounded(true);
                	
            		XMLElement arrayElement = new XMLElement(f.getName());
                	arrayElement.setType(XMLElement.Type.Sequence);
                	arrayElement.addElement(elementRef);
                	
                	document.add(arrayElement);
            		
            		fieldElements.add(f);
            	}
            	
            	else if (SchemaBasicType.fromClass(type.getComponentType()) == null)
        		{
                	defineClass(f.getName(), type.getComponentType(), false, document, classes, includeXSDData);
                	
                	fieldElements.add(f);
        		}
            	else
            	{
            		fieldAttributes.add(f);
            	}
        	}
        	else if (SchemaBasicType.fromClass(f.getType()) == null)
            {
        		defineClass(f.getName(), type, false, document, classes, includeXSDData);
        		
        		fieldElements.add(f);
            }   
        	else
        	{
        		fieldAttributes.add(f);
        	}
    	}

        for (Field f : fieldElements)
        {
        	if (f.isAnnotationPresent(XSDNestElement.class))
        	{	
        		if (f.getType().isArray())
        		{
        			if (f.isAnnotationPresent(CollapseArray.class))
        			{
        				classes.remove(f.getAnnotation(CollapseArray.class).value());
    	                
        				XMLElement fieldElement = new XMLElement(f.getName());

        	        	fieldElement.addComment(f.getAnnotation(Comment.class));
        	        	fieldElement.addTrailingComment(f.getAnnotation(TrailingComment.class));
        				fieldElement.setOptional(f.isAnnotationPresent(Optional.class));
    	                fieldElement.setUnbounded(false);
    	                fieldElement.setType(XMLElement.Type.Sequence);
        				finalElement.addElement(fieldElement);

        				XMLElement subElement = defineClass(f.getAnnotation(CollapseArray.class).value(), f.getType().getComponentType(), true, document, classes, includeXSDData);
        				subElement.setOptional(true);
        				subElement.setUnbounded(true);
        	        	
        				fieldElement.addElement(subElement);
        			}
        			else
        			{
        				XMLElement subElement = defineClass(f.getName(), f.getType().getComponentType(), true, document, classes, includeXSDData);
        				subElement.setOptional(true);
        				subElement.setUnbounded(true);
        	        	
        				finalElement.addElement(subElement);
        			}
        		}
        		else
        		{
        			throw new AssertionError("Not implemented");
        		}
        	}
        	else
        	{
	        	XMLElementRef fieldElementRef = new XMLElementRef();

        		fieldElementRef.setRef(f.getName());
	        	fieldElementRef.setOptional(f.isAnnotationPresent(Optional.class));
	        	fieldElementRef.addComment(f.getAnnotation(Comment.class));
                
                if (f.getType().isArray() && !f.isAnnotationPresent(CollapseArray.class))
                {
                	fieldElementRef.setUnbounded(true);
                	finalElement.setType(XMLElement.Type.Sequence);
                }
                else
                	fieldElementRef.setUnbounded(false);
                
        		finalElement.addElement(fieldElementRef);
        	}
        }

		if (clazz.isAnnotationPresent(Identified.class) || clazz.isAnnotationPresent(OptionallyIdentified.class))
        {
			XMLAttribute idAttribute = new XMLAttribute("id", clazz.isAnnotationPresent(Identified.class));
			
			idAttribute.addComment(new String[] { "String that identifies this element univocally across the whole document." });
			
			finalElement.addAttribute(idAttribute);
        }
        
        for (Field f : fieldAttributes)
        {
        	XMLAttribute attribute = new XMLAttribute(f.getName(), !f.isAnnotationPresent(Optional.class));
        	
        	if (f.getType().isEnum())
        	{
        		String name = f.getType().getSimpleName() + "Enum";
        		
            	defineEnum(name, f.getType(), document, f.isAnnotationPresent(Emptiable.class), classes, includeXSDData);
        		
        		attribute.setElementType(name);
        	}
        	else if (f.isAnnotationPresent(CheckType.class) && !f.getType().isArray())
        	{
        		SchemaBasicType type = SchemaBasicType.fromClass(f.getAnnotation(CheckType.class).value());

        		if (type != null)
        			attribute.setBasicType(type);
				else
				{
					String name = f.getAnnotation(CheckType.class).value().getSimpleName() + "Enum";
	        		
		            defineSimpleType(name, f.getType(), f.getAnnotation(CheckType.class).value(), document, f.isAnnotationPresent(Emptiable.class), classes, includeXSDData);
	            		
					attribute.setElementType(name);
				}
        	}
        	else if (!f.isAnnotationPresent(Reference.class) && !f.getType().isArray())
            {
        		if (SchemaBasicType.fromClass(f.getType()) != null)
        			attribute.setBasicType(SchemaBasicType.fromClass(f.getType()));
            }

        	attribute.addComment(f.getAnnotation(Comment.class));
        	
        	finalElement.addAttribute(attribute);
        }

        if (finalElement.isEmpty())
        {
        	throw new AssertionError("Empty element: " + clazz);
        }
        
        if (!hasParentElement)
        	document.add(finalElement);
        
        return finalElement;
    }

	private static XMLSimpleType defineSimpleType(String name, Class<?> type, Class<?> checkTypeClass, XObjectSchema globalStream, boolean emptiable, TreeMap<String, XMLBase> classes, TreeSet<String> includeXSDData)
	{
		if (SchemaBasicType.fromClass(checkTypeClass) != null)
			return null;

        if (classes.containsKey(name))
        {
        	if (emptiable)
        	{
	        	XMLSimpleType definedType = (XMLSimpleType) classes.get(name);
	        	
	        	if (definedType.restriction instanceof XMLEnum)
	        	{
	        		XMLEnum xmlEnum = (XMLEnum) definedType.restriction;
	        		
	        		xmlEnum.setEmptiable();
	        	}
        	}
        	
    		return null;
        }
            
		XMLRestriction restriction = null;
		
		try
		{
			String[] enumConstants = (String[]) checkTypeClass.getField("values").get(null);
			
			TreeMap<String,String> constantsPerFilter = new TreeMap<String,String>(); 
			
			for (XSDEnumFilter filter : checkTypeClass.getAnnotationsByType(XSDEnumFilter.class))
			{
				for (String constant : new TreeSet<String>(Arrays.asList((String[]) filter.value().getField("values").get(null))))
					constantsPerFilter.put(constant, filter.name());
			}

			EnumComment enumComment = checkTypeClass.getAnnotation(EnumComment.class);
			
			Method enumCommentMethod = null;
			
			if (enumComment != null)
			{
				try
				{
					enumCommentMethod = enumComment.value().getMethod("enumComment", type);
				}
				catch (NoSuchMethodException | SecurityException e)
				{
					System.err.println("EnumComment<" + checkTypeClass + "> is throwing an exception.");
				}
			}

			XMLEnum xmlEnum = new XMLEnum(emptiable);

			for (String constant : new TreeSet<String>(Arrays.asList(enumConstants)))
			{
				String constantFilterName = constantsPerFilter.get(constant);
				
				if (constantFilterName == null || includeXSDData.contains(constantFilterName))
				{
	          		String comment = null;
					
	          		if (enumCommentMethod != null)
	          		{
		          		try
		          		{
							comment = (String) enumCommentMethod.invoke(null, constant);
						}
		          		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
		          		{
		          			System.err.println("EnumComment<" + checkTypeClass + "> is throwing an exception.");
						}
	          		}
	          		
	          		xmlEnum.addValue(constant, comment);
				}
			}
			
			restriction = xmlEnum;
		} 
		catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) 
		{
			try
			{
				Field baseTypeField = checkTypeClass.getDeclaredField("baseType");
				
				baseTypeField.setAccessible(true);
				
				SchemaBasicType baseType = (SchemaBasicType) baseTypeField.get(null);
				
				restriction = new XMLBuiltinData(baseType);
			} 
			catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e2) 
			{
				restriction = new XMLBuiltinData(SchemaBasicType.XSDString);

				System.err.println("CheckType<" + checkTypeClass + "> is not restricted in XSD.");
			}				
		}
		
		XMLSimpleType simpleType = new XMLSimpleType(name, restriction);
		
		simpleType.addComment(checkTypeClass.getAnnotation(Comment.class));
		
		addTypeRestriction(checkTypeClass, "minInclusive",   simpleType, "Minimum value (inclusive): {0}. ");
		addTypeRestriction(checkTypeClass, "maxInclusive",   simpleType, "Maximum value (inclusive): {0}. ");
		addTypeRestriction(checkTypeClass, "minExclusive",   simpleType, "Minimum value (exclusive): {0}. ");
		addTypeRestriction(checkTypeClass, "maxExclusive",   simpleType, "Maximum value (exclusive): {0}. ");
		addTypeRestriction(checkTypeClass, "fractionDigits", simpleType, "Number of fractional digits : {0}. ");
		addTypeRestriction(checkTypeClass, "length",         simpleType, "String length: {0}. ");
		addTypeRestriction(checkTypeClass, "minLength",      simpleType, "Minimum string length: {0}. ");
		addTypeRestriction(checkTypeClass, "pattern",        simpleType, "This value matches the following regexp pattern: <code>{0}</code>");
		addTypeRestriction(checkTypeClass, "totalDigits",    simpleType, "Total digits: {0}. ");
		
		globalStream.add(simpleType);
		
		classes.put(name, simpleType);

		return simpleType;
	}

	private static void addTypeRestriction(Class<?> checkTypeClass, String constraint, XMLSimpleType simpleType, String predicate)
	{
		try
		{
			Field constraintField = checkTypeClass.getDeclaredField(constraint);
			constraintField.setAccessible(true);
			
			simpleType.addConstraint(constraint, constraintField.get(null).toString(), predicate);
		} 
		catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e2) 
		{
		}
	}

	private static XMLSimpleType defineEnum(String name, Class<?> type, XObjectSchema globalStream, boolean emptiable, TreeMap<String, XMLBase> classes, TreeSet<String> includeXSDData)
	{
        if (classes.containsKey(name))
        {
        	if (emptiable)
        	{
	        	XMLSimpleType definedType = (XMLSimpleType) classes.get(name);
	        	
	        	if (definedType.restriction instanceof XMLEnum)
	        	{
	        		XMLEnum xmlEnum = (XMLEnum) definedType.restriction;
	        		
	        		xmlEnum.setEmptiable();
	        	}
        	}
        	
    		return null;
        }
            
		XMLEnum xmlEnum = new XMLEnum(emptiable);

		TreeMap<String,String> constantsPerFilter = new TreeMap<String,String>(); 
		
		for (XSDEnumFilter filter : type.getAnnotationsByType(XSDEnumFilter.class))
		{
			for (Object constant : new TreeSet<Object>(Arrays.asList(filter.value().getEnumConstants())))
				constantsPerFilter.put(constant.toString(), filter.name());
		}
		
		EnumComment enumComment = type.getAnnotation(EnumComment.class);
		
		Method enumCommentMethod = null;
		
		if (enumComment != null)
		{
			try
			{
				enumCommentMethod = enumComment.value().getMethod("enumComment", type);
			}
			catch (NoSuchMethodException | SecurityException e)
			{
				System.err.println("EnumComment<" + type + "> is throwing an exception.");
			}
		}

		for (Object constant : new TreeSet<Object>(Arrays.asList(type.getEnumConstants())))
		{
			String constantFilterName = constantsPerFilter.get(constant.toString());
            
          	if (constantFilterName == null || includeXSDData.contains(constantFilterName))
          	{
          		String comment = null;
				
          		if (enumCommentMethod != null)
          		{
	          		try
	          		{
						comment = (String) enumCommentMethod.invoke(null, constant);
					}
	          		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
	          		{
	          			System.err.println("EnumComment<" + type + "> is throwing an exception.");
					}
          		}
          		
          		xmlEnum.addValue(constant.toString(), comment);
          	}
		}
		
		XMLSimpleType simpleType = new XMLSimpleType(name, xmlEnum);
		
		simpleType.addComment(type.getAnnotation(Comment.class));
		
		globalStream.add(simpleType);

		classes.put(name, simpleType);

		return simpleType;
	}
}
