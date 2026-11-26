package controllers;

import org.junit.Test;
import play.mvc.Result;

import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;

public class HomeControllerTest {
    @Test
    public void testIndex() {
        Result result = new HomeController().index();
        assertEquals(OK, result.status());
    }
}
