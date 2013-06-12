import static org.junit.Assert.*;
import java.util.Set;
import org.junit.Test;


public class LemonDifferentCorrelatedTest
{

	@Test public void testGetCorrelatedClasses()
	{
		System.out.println(LemonDifferentCorrelated.getCorrelatedClassLabels("City"));
	}
	
	@Test public void testSuperClassesOf()
	{
		System.out.println(LemonDifferentCorrelated.superClassesOf("http://dbpedia.org/ontology/City"));
	}

}