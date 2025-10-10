@import co.grtk.stableips.model.dto.UserDto
@param java.util.List<UserDto> users

@for(UserDto user : users)
<option value="${user.walletAddress()}">${user.username()} (${user.walletAddress().substring(0, 6)}...${user.walletAddress().substring(user.walletAddress().length() - 4)})</option>
@endfor
