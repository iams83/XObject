package xobject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;

import xobject.XObject.Emptiable;
import xobject.XObject.MultiReference;
import xobject.XObject.Optional;

public class XObjectStats
{
    private int nextIdentifier = 0;

    IdentifiedObjects identifiedObjects = new IdentifiedObjects();
    
    TreeMap<String,Class<?>> lostIdentifiedObjects = new TreeMap<String,Class<?>>();
    

	public void clearIdentifiers()
	{
		this.nextIdentifier = 0;
		
		this.identifiedObjects.clear();
		
		this.lostIdentifiedObjects.clear();
	}

    void identifyObject(String id, Object newObject) throws XObjectException
    {
    	Class<?> clazz = newObject.getClass();
    	
    	if (this.identifiedObjects.containsKey(id, clazz))
    	{
    		this.identifiedObjects.addDuplicated(id, clazz);
    		
    		throw new XObjectException("Duplicated key " + id, clazz);
    	}
    	else
    	{
	        this.identifiedObjects.put(id, newObject);
	        
	        this.lostIdentifiedObjects.put(id, newObject.getClass());
    	}
	}

	Object findReferencedObject(String id, Class<?> type, MultiReference multiReference) 
	{
		Object object = this.identifiedObjects.get(id, type);
		
		if (object != null)
			return object;

		if (multiReference != null)
		{
			for (Class<?> clazz : multiReference.value())
			{
				object = this.identifiedObjects.get(id, clazz);
				
				if (object != null)
					return object;
			}
		}
		
		return object;
	}
    
	String createIdentifier(Object object)
    {
		String existingKey = this.identifiedObjects.getKey(object);
		
        if (existingKey != null)
        	return existingKey;
        
        while (this.identifiedObjects.containsKey(String.valueOf(this.nextIdentifier), object.getClass()))
            this.nextIdentifier ++;
        
        String identifier = String.valueOf(this.nextIdentifier);
        
        this.identifiedObjects.put(identifier, object);
        
        return identifier;
    }
    
    class FieldStats
	{
		final public Field field;
		
		boolean foundUse, foundUnuse, foundNotEmpty;
		
		public FieldStats(Field field)
		{
			this.field = field;
		}
		
		public void reportFieldAssignment(Object newObject, Object value)
		{
			this.foundUse = true;
			
			if (value != null && !value.toString().isEmpty())
				this.foundNotEmpty = true;
		}
		
		public void reportUnusedField(Object newObject)
		{
			this.foundUnuse = true;
		}

		public void dumpReport()
		{
			if (!this.foundUnuse && this.field.isAnnotationPresent(Optional.class))
				System.out.println("SET_AS_NOT_OPTIONAL? " + this.field);

			if (!this.foundNotEmpty && this.field.isAnnotationPresent(Emptiable.class))
				System.out.println("SET_AS_NOT_EMPTIABLE? " + this.field);

			if (!this.foundUse)
				System.out.println("REMOVE? " + this.field);
		}
	}
	
	class EnumFieldStats extends FieldStats
	{
		TreeSet<String> foundValues = new TreeSet<String>();
		TreeSet<String> notFoundValues = new TreeSet<String>();
		
		public EnumFieldStats(Field field)
		{
			super(field);
			
			for (Object constant : field.getType().getEnumConstants())
				this.notFoundValues.add(constant.toString());
		}

		public void reportFieldAssignment(Object newObject, Object value)
		{
			super.reportFieldAssignment(newObject, value);
			
			if (this.notFoundValues.contains(value.toString()))
			{
				this.notFoundValues.remove(value.toString());
				this.foundValues.add(value.toString());
			}
		}
		
		public void dumpReport()
		{
			super.dumpReport();
			
			for (String constant : notFoundValues)
				System.out.println("EXTRA ENUM VALUE " + this.field.getType() + ": " + constant);
		}
	}
	
	HashMap<String,FieldStats> fields = new HashMap<String,FieldStats>();
	
	public void reportFieldAssignment(Object newObject, Field field, Object value)
	{
		FieldStats fieldStats = this.fields.get(field.toString());
		
		if (fieldStats == null)
		{
			if (field.getType().isEnum())
				fieldStats = new EnumFieldStats(field);
			else
				fieldStats = new FieldStats(field);

			this.fields.put(field.toString(), fieldStats);
		}
		
		fieldStats.reportFieldAssignment(newObject, value);
	}

	public void reportUnusedField(Object newObject, Field field)
	{
		FieldStats fieldStats = this.fields.get(field.toString());
		
		if (fieldStats == null)
		{
			if (field.getType().isEnum())
				fieldStats = new EnumFieldStats(field);
			else
				fieldStats = new FieldStats(field);
			
			this.fields.put(field.toString(), fieldStats);
		}
		
		fieldStats.reportUnusedField(newObject);
	}
	
	public void reportExtraAttributes(Object newObject, String ... foundAttributes)
	{
	}

	public void dumpReport()
	{
		for (FieldStats fieldStats : fields.values())
			fieldStats.dumpReport();
	}
}
