package xobject;

import java.io.StringWriter;
import java.lang.reflect.Field;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Node;

@SuppressWarnings("serial")
public class XObjectException extends Exception
{
    public XObjectException(Throwable e, Class<?> clazz, Field field)
    {
        super("Exception processing field. (" + (field != null ? field.getType() : null) + 
        								  " " + (field != null ? field.getName() : null) + " in " + clazz.getName() + ")", e);
    }

    public XObjectException(Throwable e, Class<?> clazz)
    {
        super("Exception processing class. (" + clazz.getName() + ")", e);
    }

    public XObjectException(Throwable e, String message, Class<?> clazz)
    {
        super(message + " (" + clazz.getName() + ")", e);
    }

    public XObjectException(String message, Class<?> clazz, Field field)
    {
        super(message + " (" + field.getType() + " " + field.getName() + " in " + clazz.getName() + ")");
    }

    public XObjectException(String message, Class<?> clazz)
    {
        super(message + " (" + clazz.getName() + ")");
    }

    public XObjectException(Throwable e, Class<?> clazz, Field field, Node node)
    {
        super("Exception processing field. (" + field.getType() + " " + field.getName() + " in " + clazz.getName() + ")\n" + xmlToString(node), e);
    }

    public XObjectException(Throwable e, Class<?> clazz, Node node)
    {
        super("Exception processing class. (" + clazz.getName() + ")\n" + xmlToString(node), e);
    }

    public XObjectException(String message, Class<?> clazz, Field field, Node node)
    {
        super(message + " (" + field.getType() + " " + field.getName() + " in " + clazz.getName() + ")\n" + xmlToString(node));
    }

    public XObjectException(String message, Class<?> clazz, Node node)
    {
        super(message + " (" + clazz.getName() + ")\n" + xmlToString(node));
    }
    
    static public String xmlToString(Node node)
    {
    	try
    	{
	    	StringWriter writer = new StringWriter();
	
	        DOMSource domSource = new DOMSource(node);
	        StreamResult result = new StreamResult(writer);
	        TransformerFactory tf = TransformerFactory.newInstance();
	        Transformer transformer = tf.newTransformer();
	        transformer.transform(domSource, result);
	        
	        return writer.toString();
    	}
    	catch(TransformerException e)
    	{
    		return null;
    	}
    }
}
