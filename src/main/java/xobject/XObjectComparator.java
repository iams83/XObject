package xobject;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import xobject.XObject.ObjectMembersComparable;
import xobject.XObject.ValueComparator;
import xobject.XObject.XObjectTruncatedComparison;

public class XObjectComparator
{
	static final public boolean LET_EMPTY_ARRAYS_BE_EQUAL_TO_NULL = true;
	
    public static class ObjectComparator implements Comparator<Object>
    {
        @Override
        public int compare(Object o1, Object o2)
        {
            return ObjectComparator.compare(o1, o2, null);
        }
        
        static public int compare(Object o1, Object o2, ValueComparator valueComparator)
        {
            try
            {
            	XObjectComparator comparator = new XObjectComparator(new XObjectStats(), new XObjectStats());
            	
            	comparator.compare(o1, o2, valueComparator);
            	
            	return comparator.getResult();
            }
            catch (XObjectException e)
            {
                throw new RuntimeException(e);
            }
        }
    };
    
    static final public ObjectComparator OBJECT_COMPARATOR = new ObjectComparator();

    public static class ImageComparator implements Comparator<Image>
    {
        @Override
        public int compare(Image o1, Image o2)
        {
            return ImageComparator.compare(o1, o2, null);
        }
        
        static public int compare(Image o1, Image o2, ValueComparator valueComparator)
        {
        	int diff = 0;
        	if( o1 == null ^ o2 == null )
        		diff = o1 == null ? 1 : -1;
        	
        	if( o1 == null && o2 == null )
        		return diff;
        	
        	if( diff == 0 )
        	{
        		diff = o1.getWidth(null) - o2.getWidth(null);
        	}
        	
        	if( diff == 0 )
        	{
        		diff = o1.getHeight(null) - o2.getHeight(null);
        	}
        	
        	if( diff == 0 )
        	{
        		if( o1 instanceof BufferedImage && o2 instanceof BufferedImage )
        		{
	        		final BufferedImage bufferedImage1 = (BufferedImage)o1;
	        		final BufferedImage bufferedImage2 = (BufferedImage)o2;
	        		
	        		final byte[] pixels1 = ((DataBufferByte)bufferedImage1.getRaster().getDataBuffer()).getData();
	        		final byte[] pixels2 = ((DataBufferByte)bufferedImage2.getRaster().getDataBuffer()).getData();

	        		if( bufferedImage1.getAlphaRaster() != bufferedImage2.getAlphaRaster() )
	        			diff = bufferedImage1.getAlphaRaster() != null ? 1 : -1;
	        		
	        		if( diff == 0 )
	        		{
	        			diff = pixels1.length - pixels2.length; 
	        		}

	        		if( diff == 0 )
	        		{
	        			final int maxDifferences = 1;
	        			
	        			if( bufferedImage1.getAlphaRaster() != null )
	        			{
			        		final int pixelLength = 4;
	        				int numDifferences = 0;
			        		for( int i=0; i<pixels1.length; i+=pixelLength )
			        		{
			        			final int diffAlpha = pixels1[i]     - pixels2[i];
			        			final int diffBlue  = pixels1[i + 1] - pixels2[i + 1];
			        			final int diffGreen = pixels1[i + 2] - pixels2[i + 2];
			        			final int diffRed   = pixels1[i + 3] - pixels2[i + 3];
			        			
			        			final int diffAlphaAbs = Math.abs(diffAlpha);
			        			final int diffBlueAbs  = Math.abs(diffBlue);
			        			final int diffGreenAbs = Math.abs(diffGreen);
			        			final int diffRedAbs   = Math.abs(diffRed);
			        			
			        			if( diffAlphaAbs > 5 || diffBlueAbs > 5 || diffGreenAbs > 5 || diffRedAbs > 5 || diffAlphaAbs + diffBlueAbs + diffGreenAbs + diffRedAbs > 10 )
			        			{
			        				numDifferences++;
			        				if( numDifferences == maxDifferences )
			        				{
				        				if( diffAlpha != 0 )
				        					diff = diffAlpha;
				        				else if( diffBlue != 0 )
				        					diff = diffBlue;
				        				else if( diffGreen != 0 )
				        					diff = diffGreen;
				        				else
				        					diff = diffRed;
				        				break;
			        				}
			        			}
	        				}
	        			}
	        			else
	        			{
			        		final int pixelLength = 3;
	        				int numDifferences = 0;
			        		for( int i=0; i<pixels1.length; i+=pixelLength )
			        		{
			        			final int diffBlue  = pixels1[i]     - pixels2[i];
			        			final int diffGreen = pixels1[i + 1] - pixels2[i + 1];
			        			final int diffRed   = pixels1[i + 2] - pixels2[i + 2];
			        			
			        			final int diffBlueAbs  = Math.abs(diffBlue);
			        			final int diffGreenAbs = Math.abs(diffGreen);
			        			final int diffRedAbs   = Math.abs(diffRed);
			        			
			        			if( diffBlueAbs > 5 || diffGreenAbs > 5 || diffRedAbs > 5 || diffBlueAbs + diffGreenAbs + diffRedAbs > 10 )
			        			{
			        				numDifferences++;
			        				if( numDifferences == maxDifferences )
			        				{
				        				if( diffBlue != 0 )
				        					diff = diffBlue;
				        				else if( diffGreen != 0 )
				        					diff = diffGreen;
				        				else
				        					diff = diffRed;
			        					break;
			        				}
			        			}
	        				}
	        			}
	        		}
        		}
        	}
        	
        	return diff;
        }
    }
    
	static public class SkipComparison
	{
		static public int compare(Object object1, Object object2) throws XObjectException
		{
			return 0;
		}
	}
	
	static public class FieldValue
	{
		final public Field field;
		final public Object o1, o2;
		
		public FieldValue(Field field, Object o1, Object o2)
		{
			this.field = field;
			this.o1 = o1;
			this.o2 = o2;
		}
	}
	
	static public class ComparisonDifference
	{
		final private XObjectStats stats1, stats2; 
		final private ArrayList<FieldValue> fields;
		final public int result;
		
		ComparisonDifference(XObjectStats stats1, XObjectStats stats2, ArrayList<FieldValue> fields, int result)
		{
			this.stats1 = stats1;
			this.stats2 = stats2;
			this.fields = new ArrayList<FieldValue>(fields);
			this.result = result;
			
			Collections.reverse(this.fields);
		}
		
		@Override
		public String toString()
		{
			String s = "";
			
			for (FieldValue field : this.fields)
			{
				if (!s.isEmpty())
					s += "\n\tfrom ";
				
				s += field.field.getName() + "\n" +
					"\t\texpected: " + XObject.toString(field.o1, this.stats1) + "\n" +
					"\t\tfound:    " + XObject.toString(field.o2, this.stats2);
			}
			
			return s;
		}

		public Object getO1()
		{
			return this.fields.get(0).o1;
		}

		public Object getO2()
		{
			return this.fields.get(0).o2;
		}

		public String getDifferenceName()
		{
			String s = null;
			
			for (FieldValue field : this.fields)
			{
				String fieldName = field.field.getName();
				
				if (fieldName.equals("SUMMARY"))
				{
					// Do not write this field name AND stop writing parents
					
					break;
				}	

				if (fieldName.equals("group"))	// ELEVATION.group
				{
					// Do not write this attribute as it is useless, but continue writing parents
				}
				else
				{
					if (s == null)
						s = fieldName;
					else if (!s.startsWith(fieldName + "."))
						s = fieldName + "." + s;
				}
				
				if (fieldName.equals("WALL") ||
					fieldName.equals("ROOF") ||
					fieldName.equals("STRUCTURE") ||
					fieldName.equals("EXTERIOR") ||
					fieldName.equals("ELEMENTS") ||
					fieldName.equals("ELEVATION") ||
					fieldName.equals("PRIMITIVES") ||
					fieldName.equals("ATTRIBUTE") ||
					fieldName.equals("POST_VALIDATOR_RESULTS") ||
					fieldName.equals("PRE_VALIDATOR_RESULTS") ||
					fieldName.equals("CORNICE_END"))
				{
					// Write this field name BUT stop writing parents
					
					break;
				}	
			}
			
			if (s == null)
				return "";
			
			return s;
		}
	}

	static class StartedComparison
	{
		final public Object o1, o2;

		public StartedComparison(Object o1, Object o2)
		{
			this.o1 = o1;
			this.o2 = o2;
		}
	}
	
	static class CachedComparison
	{
		final public Object o1, o2;
		
		final public ComparisonDifference difference;
		
		public CachedComparison(Object o1, Object o2, ComparisonDifference difference)
		{
			this.o1 = o1;
			this.o2 = o2;
			this.difference = difference;
		}
	}
	
	final private ArrayList<StartedComparison> startedComparison = new ArrayList<StartedComparison>();
	
	final private ArrayList<CachedComparison> cachedComparison = new ArrayList<CachedComparison>();
	
	final private ArrayList<ComparisonDifference> differences = new ArrayList<ComparisonDifference>();
	
	final private ArrayList<FieldValue> fields = new ArrayList<FieldValue>();
	
	final private XObjectStats stats1, stats2;

	public XObjectComparator(XObjectStats stats1, XObjectStats stats2)
	{
		this.stats1 = stats1;
		this.stats2 = stats2;
	}

	public ComparisonDifference registerComparison(Object o1, Object o2, int result)
	{
		ComparisonDifference c = new ComparisonDifference(this.stats1, this.stats2, fields, result);
		
		if (c.result != 0)
		{
			for (ComparisonDifference d : this.differences)
			{
				if ((d.getO1() == o1 && d.getO2() == o2) || 
					(d.getO1() == o2 && d.getO2() == o1))
				{
					return c;
				}
			}
			
			this.differences.add(c);
		}
		
		putCachedComparison(o1, o2, c);
		return c;
	}

	private void putCachedComparison(Object o1, Object o2, ComparisonDifference comparisonResult)
	{
		this.cachedComparison.add(new CachedComparison(o1, o2, comparisonResult));
	}

	private CachedComparison startComparison(Object o1, Object o2)
	{
		for (CachedComparison c : cachedComparison)
		{
			if (c.o1 == o1 && c.o2 == o2)
				return c;
		}
		
		for (StartedComparison c : startedComparison)
		{
			if (c.o1 == o1 && c.o2 == o2)
				return new CachedComparison(o1, o2, this.registerComparison(o1, o2, 0));
			
			else if (c.o1 == o2 && c.o2 == o1)
				return new CachedComparison(o2, o1, this.registerComparison(o1, o2, 0));
		}
		
		startedComparison.add(new StartedComparison(o1, o2));
		
		return null;
	}
	
	public int getResult()
	{
		return this.differences.isEmpty() ? 0 : this.differences.get(0).result;
	}
	
    public ArrayList<ComparisonDifference> compare(Object object1, Object object2) throws XObjectException
    {
    	this._compare(object1, object2, null);
    	
    	return this.differences;
    }
    
    public ArrayList<ComparisonDifference> compare(Object object1, Object object2, ValueComparator valueComparer) throws XObjectException
    {
    	if (!this.fields.isEmpty())
    		throw new AssertionError("This code should never be reached");
    	    	
    	this._compare(object1, object2, valueComparer);
    	
    	if (!this.fields.isEmpty())
    		throw new AssertionError("This code should never be reached");
    	    	
    	return this.differences;
    }
    
    private void _compare(Object object1, Object object2, ValueComparator valueComparer) throws XObjectException
    {
    	if (valueComparer != null && valueComparer.value() == SkipComparison.class)
    		return;
    	
    	if (object1 == object2)
    	{
    		this.registerComparison(object1, object2, 0);
    		return;
    	}
    	
        if (object1 == null)
        {
            this.registerComparison(object1, object2,  object2 == null || 
            		(LET_EMPTY_ARRAYS_BE_EQUAL_TO_NULL && object2.getClass().isArray() && ((Object[]) object2).length == 0) ? 0 : 1);
            return;
        }

        if (object2 == null)
        {
            this.registerComparison(object1, object2, 
            		(LET_EMPTY_ARRAYS_BE_EQUAL_TO_NULL && object1.getClass().isArray() && ((Object[]) object1).length == 0) ? 0 : -1);
            return;
        }
        
        Class<?> clazz  = object1.getClass();
        Class<?> clazz2 = object2.getClass();
        
		try
		{
	        if (clazz != clazz2)
	        {
	            this.registerComparison(object1, object2, clazz.getName().compareTo(clazz2.getName()));
	            return;
	        }
	        
	        else if (!clazz.isArray())
	        {
	        	CachedComparison cachedComparison = this.startComparison(object1, object2);
	        	
	        	if (cachedComparison != null)
	        	{
	        		this.registerComparison(object1, object2, cachedComparison.difference.result);
	        	}

	        	else if (valueComparer != null)
	        	{
	    			Object comparer = valueComparer.value().newInstance();
				
	    			if( comparer instanceof XObjectTruncatedComparison )
	    			{
	    				  ((XObjectTruncatedComparison)comparer).SetDecimalDigits(valueComparer.DecimalDigits());
	    			}
	    			
	    			Method method;
	    			
	    			try
	    			{
	    				method = comparer.getClass().getMethod("compare", clazz, clazz);
	    			}
	    			catch(NoSuchMethodException e)
	    			{
	    				try
	    				{
	    					method = comparer.getClass().getMethod("compare", Object.class, Object.class);
	    				}
		    			catch(NoSuchMethodException e1)
		    			{
		    				method = null;
		    			}
	    			}
	    			
	    			if (method != null)
	    			{
		    			int comparisonResult;
		    			
	    				comparisonResult = (int) method.invoke(comparer, object1, object2);

						this.registerComparison(object1, object2, comparisonResult);
	    			}
	    			else
	    			{
	    				try
	    				{
	    					method = comparer.getClass().getMethod("compare", XObjectComparator.class, clazz, clazz);
	    				}
		    			catch(NoSuchMethodException e)
		    			{
		    				method = comparer.getClass().getMethod("compare", XObjectComparator.class, Object.class, Object.class);
		    			}
	    				
	    				method.invoke(comparer, this, object1, object2);
	    			}
	        	}
	        	
        		else if (clazz.equals(String.class))
	                this.registerComparison(object1, object2, ((String) object1).compareTo((String) object2));
	            
	        	else if (clazz.equals(int.class))
		            this.registerComparison(object1, object2, Integer.compare((int) object1, (int) object2));
		        
		        else if (clazz.equals(Integer.class))
		            this.registerComparison(object1, object2, Integer.compare((Integer) object1, (Integer) object2));
		        
		        else if (clazz.equals(long.class))
		            this.registerComparison(object1, object2, Long.compare((long) object1, (long) object2));
		        
		        else if (clazz.equals(Long.class))
		            this.registerComparison(object1, object2, Long.compare((Long) object1, (Long) object2));
		        
		        else if (clazz.equals(float.class))
		            this.registerComparison(object1, object2, Float.compare((float) object1, (float) object2));
		        
		        else if (clazz.equals(Float.class))
		            this.registerComparison(object1, object2, Float.compare((Float) object1, (Float) object2));
		        
		        else if (clazz.equals(double.class))
		            this.registerComparison(object1, object2, Double.compare((double) object1, (double) object2));
		        
		        else if (clazz.equals(Double.class))
		            this.registerComparison(object1, object2, Double.compare((Double) object1, (Double) object2));
		        
		        else if (clazz.equals(boolean.class))
		            this.registerComparison(object1, object2, Boolean.compare((boolean) object1, (boolean) object2));
		        
		        else if (clazz.equals(Boolean.class))
		            this.registerComparison(object1, object2, Boolean.compare((Boolean) object1, (Boolean) object2));

		        else if (clazz.isEnum())
		            this.registerComparison(object1, object2, object1.toString().compareTo(object2.toString()));

		        else 
		        {
		        	boolean wasCompared = false;

		        	try
		        	{
			        	Method compareMethod = clazz.getMethod("preComparison", clazz, clazz);
			        	
			        	int comparisonResult = (int) compareMethod.invoke(null, object1, object2);
			        	
			        	if (comparisonResult != 0)
			        	{
			        		this.registerComparison(object1, object2, comparisonResult); 

			        		return;
			        	}
			        	
			        	wasCompared = true;
		        	}
		        	catch(NoSuchMethodException e)
		        	{
		        		// Do nothing
		        	}
		        	
		        	if (clazz.isAnnotationPresent(ObjectMembersComparable.class))
		            {
		    	        for (Field field1 : XObject.getClassHierarchyFields(clazz))
		    	        {
		    	            try
		    	            {
		    	                field1.setAccessible(true);
		    	                
		    	                Object v1 = field1.get(object1);
		    	                Object v2 = field1.get(object2);
	
		    	                try
		    	                {
			    	            	this.fields.add(new FieldValue(field1, v1, v2));
			    	            	
			    	            	ValueComparator fieldValueComparer = field1.getAnnotation(ValueComparator.class);
			    	            	
			    	            	if (fieldValueComparer != null && fieldValueComparer.value() == SkipComparison.class)
			    	            	{
			    	            		// Do not compare
			    	            	}
			    	            	else if (v1 == null && v2 != null)
			    	                {
			    	                	this.registerComparison(object1, object2, LET_EMPTY_ARRAYS_BE_EQUAL_TO_NULL && v2.getClass().isArray() && ((Object[]) v2).length == 0 ? 0 : 1);
			    	                    return;
			    	                }
			    	            	else if (v1 != null)
			    	                {
			    	                	if (v2 == null)
			    	                	{
			    	                		this.registerComparison(object1, object2, LET_EMPTY_ARRAYS_BE_EQUAL_TO_NULL && v1.getClass().isArray() && ((Object[]) v1).length == 0 ? 0 : -1);
			    	                		return;
			    	                	}
			    	                	
			    	                	compareFields(field1, v1, v2);
			    	                }
		    	                }
			    	            finally
			    	            {
			    	            	this.fields.remove(this.fields.size() - 1);
			    	            }
		    	            }
		            		catch(XObjectException e)
		            		{
		            			throw new XObjectException(e, clazz, field1);
		            		}
		    	            catch(IllegalAccessException e)
		    	            {
		    	                throw new XObjectException(e, clazz, field1);
		    	            }
		    	        }
		    	        
		    	        wasCompared = true;
		            }

		        	try
		        	{
			        	Method compareMethod = clazz.getMethod("postComparison", clazz, clazz);
			        	
			        	int comparisonResult = (int) compareMethod.invoke(null, object1, object2);
			        	
			        	if (comparisonResult != 0)
			        	{
			        		this.registerComparison(object1, object2, comparisonResult); 

			        		return;
			        	}
			        	
			        	wasCompared = true;
		        	}
		        	catch(NoSuchMethodException e)
		        	{
		        		// Do nothing
		        	}
		        	
        			if (wasCompared)
        			{
		    	        this.registerComparison(object1, object2, 0);
        			}
        			else
		        	{
		        		throw new XObjectException("Could not compare objects", clazz);		        		
		        	}
	            }
	        }
	        else
	        {
                Object[] array1 = (Object[]) object1;
                Object[] array2 = (Object[]) object2;
             
                if (valueComparer == null || valueComparer.value() != SkipComparison.class)
                {
	                if (array1.length != array2.length)
	                {
	                	this.registerComparison(object1, object2, Integer.compare(array1.length, array2.length));
	                	return;
	                }
	                
	            	Field field = this.fields.get(this.fields.size() - 1).field;
	            	
	                for (int i = 0; i < array1.length; i ++)
	                {
		                try
		                {
		                	this.fields.add(new FieldValue(field, array1[i], array2[i]));
	    	            	
	    	            	this.compareFields(field, array1[i], array2[i]);
		                }
		                finally
		                {
		                	this.fields.remove(fields.size() - 1);
		                }
	                }
                }
	        }
	
	    	this.registerComparison(object1, object2, 0);
		}
		catch(InvocationTargetException e)
		{
			throw new XObjectException(e.getTargetException(), clazz);
		}
		catch(IllegalAccessException | InstantiationException | IllegalArgumentException | NoSuchMethodException | SecurityException e)
		{
			throw new XObjectException(e, clazz);
		}
    }

	public void compareFields(Field field, Object object1, Object object2) throws XObjectException
	{
		ValueComparator memberValueComparator = field.getAnnotation(ValueComparator.class);
		
		Class<?> type = field.getType();
		
		if (type.isArray())
			type = type.getComponentType();
		
		if (memberValueComparator == null && type.isAnnotationPresent(ValueComparator.class))
			memberValueComparator = type.getAnnotation(ValueComparator.class);
				
		this._compare(object1, object2, memberValueComparator);
	}
}
