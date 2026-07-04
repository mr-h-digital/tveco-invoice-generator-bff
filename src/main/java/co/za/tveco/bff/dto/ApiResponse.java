package co.za.tveco.bff.dto;

public record ApiResponse<T>(boolean success, T data) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data);
    }
}
