import java.util.List;

public record VisitorDto(
        String name,
        String surname,
        String phone,
        boolean subscribed,
        List<BookDto> favoriteBooks
) {}