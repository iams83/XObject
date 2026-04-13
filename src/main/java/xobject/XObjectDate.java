package xobject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

@SuppressWarnings("serial")
public class XObjectDate extends GregorianCalendar
{
    final private SimpleDateFormat dateFormat;
    
    public XObjectDate(SimpleDateFormat dateFormat, String str) throws ParseException
    {
        this.dateFormat = dateFormat;
        
        this.setTime(dateFormat.parse(str));
    }
    
    public String toString()
    {
        return dateFormat.format(this.getTime());
    }
}
