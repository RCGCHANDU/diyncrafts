// package com.diyncrafts.webapp.security;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.security.core.userdetails.UserDetails;
// import org.springframework.security.core.userdetails.UserDetailsService;
// import org.springframework.security.core.userdetails.UsernameNotFoundException;
// import org.springframework.stereotype.Service;

// import com.diyncrafts.webapp.model.User;
// import com.diyncrafts.webapp.repository.jpa.UserRepository;

// @Service
// public class CustomUserDetailsService implements UserDetailsService {
//     @Autowired
//     private UserRepository userRepository;

//     @Override
//     public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//         return userRepository.findByUsername(username)
//             .orElseThrow(() -> new UsernameNotFoundException("User not found"));
//     }
// }