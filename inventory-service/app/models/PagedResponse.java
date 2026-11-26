package models;

import java.util.List;

/**
 * Cấu trúc dữ liệu chuẩn dùng để trả về danh sách kết quả có phân trang
 * (phiên bản không dùng generic).
 */
public class PagedResponse {
    // Danh sách phần tử thuộc trang hiện tại (có thể ép kiểu hoặc dùng kiểu cụ thể nếu cần).
    private List<Object> items;
    // Chỉ số trang hiện tại (thường bắt đầu từ 0 hoặc 1 tùy quy ước API).
    private int page;
    // Số lượng phần tử tối đa trên mỗi trang.
    private int size;
    // Tổng số bản ghi khớp điều kiện trên toàn bộ tập dữ liệu (dùng long để tránh tràn số).
    private long total;

    /**
     * Khởi tạo đối tượng phản hồi phân trang đầy đủ thông tin.
     */
    public PagedResponse(List<Object> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    // Các phương thức getter truy xuất dữ liệu phân trang.
    public List<Object> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotal() { return total; }
}