package de.fearnixx.jeak.service.controller.testImpls;

import de.fearnixx.jeak.service.controller.connection.RequestMethod;
import de.fearnixx.jeak.service.controller.reflect.RequestBody;
import de.fearnixx.jeak.service.controller.reflect.RequestMapping;
import de.fearnixx.jeak.service.controller.reflect.RequestParam;
import de.fearnixx.jeak.service.controller.reflect.RestController;

@RestController(pluginId = "pid", endpoint = "/test")
public class TestController {

    @RequestMapping(method = RequestMethod.GET, endpoint = "/hello")
    public DummyObject hello() {
        return new DummyObject("Finn", 20);
    }

    @RequestMapping(method =  RequestMethod.GET, endpoint = "/info/:name")
    public String returnSentInfo(@RequestParam(name = "name") String name) {
        return "received " + name;
    }

    @RequestMapping(method = RequestMethod.POST, endpoint = "/body")
    public String sendBody(@RequestBody(type = String.class, name = "string") String string) {
        return "this is the body " + string;
    }
}