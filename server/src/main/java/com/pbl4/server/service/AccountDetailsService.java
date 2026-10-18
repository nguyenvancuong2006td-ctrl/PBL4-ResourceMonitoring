package com.pbl4.server.service;

import com.pbl4.server.model.Account;
import com.pbl4.server.repository.AccountRepository;
import org.springframework.stereotype.Service;

@Service
public class AccountDetailsService {

    private final AccountRepository accountRepository;

    public AccountDetailsService(
            AccountRepository accountRepository) {

        this.accountRepository = accountRepository;
    }

    public Account getAccountByUsername(String username) {

        return accountRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy tài khoản: " + username
                        )
                );
    }
}