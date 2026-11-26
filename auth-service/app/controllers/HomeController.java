package controllers;

import play.mvc.*;

/** Controller xử lý yêu cầu truy cập trang chủ của ứng dụng. */
public class HomeController extends Controller {

    /** Hiển thị trang chủ khi nhận yêu cầu GET tại đường dẫn /. */
    public Result index() {
        return ok(views.html.index.render());
    }

}
