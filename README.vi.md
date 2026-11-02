**Tiếng Việt** | [English](README.en.md)

---

# SuperBattery ⚡

là ứng dụng Android chuyên dụng hỗ trợ theo dõi mức pin và điện áp dành riêng cho các thiết bị Android đã can thiệp phần cứng: **độ cell pin Li-ion 3.7V (đầy ở 4.2V)** và sử dụng **mạch sạc ngoài mắc song song BMS pin** thay thế cho chuẩn pin Li-Po 3.85V/4.4V mặc định của hệ thống.

---

## 📌 Tác giả muốn gì?
_"Mình sử dụng 1 thẻ SIM để gọi điện, và 1 thẻ SIM để kết nối internet. Vì điện thoại chính của mình chỉ hỗ trợ DSDS - cả 2 thẻ SIM có thể mở sóng nhưng chỉ 1 thẻ SIM có thể kết nối internet cùng lúc, nên internet của mình sẽ bị mất khi nói chuyện điện thoại trên thẻ SIM gọi. Thậm chí, chỉ cần thẻ SIM gọi điện đang đổ chuông là internet cũng bị cắt. Mình muốn khắc phục tình trạng này bằng việc tận dụng 1 chiếc điện thoại cũ, tháo bỏ pin, giữ lại BMS, hàn song song BMS điện thoại, pin, và mạch sạc ngoài, để bắt sóng 4G và phát qua WiFi. Vấn đề được đặt ra là, làm thế nào để biết pin đầy hay chưa? Viên pin được sạc đầy bằng mạch điện bên ngoài, bỏ qua hoàn toàn mạch sạc của điện thoại, nên vạch báo pin của Android sẽ đóng băng ở 1%."_
* **Đo pin theo điện áp thực tế của cell 3.7V** sau đó hiển thị dung lượng pin dựa trên mức điện áp.
* **Cảnh báo điện áp thấp** qua thông báo hệ thống khi pin chạm ngưỡng xả sâu để tránh làm chai pin.


_"Mình nghĩ 1 ứng dụng chỉ để đọc pin là chưa đủ cho 1 thiết bị chuyên phát sóng WiFi. Nên bổ sung thêm gì nhỉ?"_
* **Biểu đồ theo dõi** trực quan lịch sử dao động điện áp, nhiệt độ pin, dung lượng RAM khả dụng theo thời gian thực.
* **Màn hình chính ứng dụng** hiển thị các biểu đồ, phần trăm pin, và lưu lượng internet.
* **Bộ Widget đa dạng** để hiển thị % pin, nhiệt độ, điện áp và ngày tháng.
* **Đếm thiết bị kết nối điểm phát sóng di động** trên widget.
* **Hiển thị âm lịch Việt Nam** trên widget.


_"Nếu đã độ pin lớn, tại sao không dùng nó làm bộ sạc dự phòng khẩn cấp nhỉ?"_
* **Sạc được** cho những thiết bị khác.

---

## 📸 Hình ảnh thực tế (Showcase)

### 1. Giao diện & Widget
| Màn hình chính & Widget | Giao diện ứng dụng chính |
| :---: | :---: |
| ![Màn hình chính & Widget](homescr.avif) | ![Giao diện ứng dụng chính](dashbo.avif) |
| *Widget hiển thị nhiệt độ pin, trạng thái phát sóng, ngày âm/dương, % pin, điện áp pin.* | *Màn hình theo dõi lưu lượng mạng, đồ thị điện áp, RAM khả dụng và nhiệt độ pin theo thời gian thực. RAM 67.* |

### 2. Thiết kế phần cứng & Thoát nhiệt

**Thông tin điện thoại và vỏ sạc dự phòng sử dụng trong dự án:**
> * **Redmi Note 9S** 4G, 4GB RAM, 64GB UFS.
> * **Sạc dự phòng** RP-PB172 (Sạc dự phòng 20000mAh đa năng)
> * **Dung lượng thiết kế gốc:** 2 × 10,000mAh / 3.7V (74Wh).
> * **Dung lượng thực tế:** 1 × 10,000mAh / 3.7V (37Wh), kích thước 1260110.
> * **Thông số sạc hỗ trợ:**
>   * Input: Micro-USB (5V=2.1A), Type-C / Lightning (5V=2A, 9V=2A, 12V=1.5A)
>   * IN/OUT (Type-C): 5V=3A, 9V=2A, 12V=1.5A (Max 18W)
>   * Output (iSmart 1): 5V=3A, 9V=2A, 12V=1.5A
>   * Output (iSmart 2): 5V=2.4A
> * **Từ khoá tìm kiếm:** `RP-PB172` `RAVPower RP-PB172`

| Cụm mạch sạc dự phòng | Khe hở đối lưu thoát nhiệt |
| :---: | :---: |
| ![Cụm mạch sạc dự phòng](pobank.avif) | ![Khe hở thoát nhiệt](gap.avif) |
| *Tận dụng phần vỏ và mạch sạc dự phòng để làm mạch sạc ngoài cho điện thoại.* | *Chừa một khe hở giữa thân điện thoại và vỏ sạc dự phòng để tối ưu tản nhiệt thụ động khi phát mạng liên tục.* |

---

## ⚠️ Tuyên bố miễn trừ trách nhiệm (Disclaimer)
1. Việc độ chế cell pin, hàn nối mạch sạc ngoài và can thiệp phần cứng điện thoại tiềm ẩn rủi ro cháy nổ, hư hỏng linh kiện hoặc chập mạch nếu không có kiến thức kĩ thuật phù hợp.
2. Tác giả **không chịu bất kì trách nhiệm nào** đối với mọi sự cố phần cứng, mất dữ liệu, chập cháy hoặc tai nạn phát sinh từ việc can thiệp phần cứng thiết bị của bạn.
3. **Bản chất dự án** là ban đầu được xây dựng cho nhu cầu cá nhân: **Biến điện thoại cũ thành trạm phát sóng 4G**. Phần logic thuật toán và giải pháp do tác giả tự xây dựng; mã nguồn được hỗ trợ sinh và hoàn thiện bởi AI.
4. **Đã thử nghiệm và hiển thị tốt nhất** trên:
* Redmi Note 9S 4G - 4GB-64GB, mật độ điểm ảnh 395 ppi, độ phân giải 1080x2400.
* Redmi Note 10S 4G - 8GB-128GB, mật độ điểm ảnh 409 ppi, độ phân giải 1080x2400.
5. Các màn hình có độ phân giải 1080x2400 với mật độ từ 395 đến 409 ppi **có thể** sẽ hiển thị tốt.
6. Các màn hình có thông số khác có thể bị xuống dòng hoặc xuất hiện ô trống.
7. Các thiết bị có màn hình tỉ lệ ngắn hơn 9x20 và các thiết bị đang xoay ngang màn hình **chắc chắn** sẽ phải cuộn trang trên màn hình app chính.

---

## ⚙️ Lưu ý kĩ thuật & Tương thích phần cứng (Technical Notes)

Ứng dụng được may đo và tối ưu hoá dựa trên phần cứng thực nghiệm cụ thể, vui lòng lưu ý các điểm sau khi tham khảo hoặc tái sử dụng:

* **Bảng tra cứu điện áp xả (Discharge Curve):**
  * Dải điện áp được nén an toàn trong khoảng **3550 mV (0%) – 4140 mV (100%)**.
  * Ngưỡng sạc đầy được đặt ở **4140 mV** (thay vì 4200 mV) để khớp với điện áp thực tế khi ngắt sạc qua dây nối và mạch sạc dự phòng ngoài.
  * Ngưỡng xả cạn dừng ở **3550 mV** nhằm bảo vệ cell pin và tránh hiện tượng sụt áp làm sập nguồn đột ngột khi modem 4G phát sóng công suất cao.
* **Cơ chế đếm thiết bị Hotspot:**
  * Widget nhận diện số máy kết nối bằng cách **đọc thông báo hệ thống**, vì vậy nó cần được cấp quyền này.
  * Thiết bị **bắt buộc** phải chạy MIUI 14 và cài đặt ngôn ngữ hệ thống là Tiếng Anh - Anh (English - UK) để đọc chính xác các chuỗi thông báo (`"Tethering or hotspot active"`, `"connected devices"`).
  * Chưa thử nghiệm trên bất kì giao diện nào khác.
* **Ngưỡng nhiệt độ:**
  * Hệ thống sẽ kích hoạt cảnh báo nguy cấp khi nhiệt độ pin chạm ngưỡng **≥ 45°C**.
* **Âm lịch:**
  * Cố định tính toán theo múi giờ **GMT+7** và hiển thị tên Can Chi theo tiếng Việt.
 
---

## 📜 Bản quyền & Ghi nhận (Credits & License)
* **Mã nguồn ứng dụng:** Phát hành theo giấy phép [MIT License](LICENSE).
* **Thuật toán Âm lịch:** Module `VietnameseLunarCalendar.kt` sử dụng thuật toán tính Âm lịch Việt Nam của **Hồ Ngọc Đức**.

---

## 🛠️ Cài đặt & Đóng góp
Dự án được xây dựng bằng **Android Studio**, ngôn ngữ **Kotlin**.
Mọi đóng góp nhằm tối ưu hoá bảng tra cứu điện áp (discharge curve) cho các loại cell pin khác nhau hoặc hoàn thiện giao diện đều rất được hoan nghênh.

