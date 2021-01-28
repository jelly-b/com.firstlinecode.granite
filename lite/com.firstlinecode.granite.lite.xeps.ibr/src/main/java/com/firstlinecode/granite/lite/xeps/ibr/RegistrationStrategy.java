package com.firstlinecode.granite.lite.xeps.ibr;

import org.springframework.stereotype.Component;

import com.firstlinecode.granite.framework.core.auth.Account;
import com.firstlinecode.granite.framework.core.commons.utils.StringUtils;
import com.firstlinecode.granite.framework.core.supports.data.IDataObjectFactory;
import com.firstlinecode.granite.framework.core.supports.data.IDataObjectFactoryAware;
import com.firstlinecode.granite.xeps.ibr.IRegistrationStrategy;
import com.firstlinecode.granite.xeps.ibr.MalformedRegistrationInfoException;
import com.firstlinecode.basalt.xeps.ibr.IqRegister;
import com.firstlinecode.basalt.xeps.ibr.RegistrationField;
import com.firstlinecode.basalt.xeps.ibr.RegistrationForm;

@Component
public class RegistrationStrategy implements IRegistrationStrategy, IDataObjectFactoryAware {
	private IDataObjectFactory dataObjectFactory;

	@Override
	public IqRegister getRegistrationForm() {
		RegistrationField username = new RegistrationField("username");
		RegistrationField password = new RegistrationField("password");
		RegistrationField email = new RegistrationField("email");
		
		RegistrationForm form = new RegistrationForm();
		form.getFields().add(username);
		form.getFields().add(password);
		form.getFields().add(email);
		
		IqRegister iqRegister = new IqRegister();
		iqRegister.setRegister(form);
		
		return iqRegister;
	}

	@Override
	public Account convertToAccount(IqRegister iqRegister) throws MalformedRegistrationInfoException {
		RegistrationForm form = (RegistrationForm)iqRegister.getRegister();
		Account account = dataObjectFactory.create(Account.class);
		for (RegistrationField field : form.getFields()) {
			if ("username".equals(field.getName())) {
				account.setName(field.getValue());
			} else if ("password".equals(field.getName())) {
				account.setPassword(field.getValue());
			} else {
				// ignore
			}
		}
		
		if (StringUtils.isEmpty(account.getName()) || StringUtils.isEmpty(account.getPassword())) {
			throw new MalformedRegistrationInfoException("Null user name or password.");
		}
		
		return account;
	}

	@Override
	public void setDataObjectFactory(IDataObjectFactory dataObjectFactory) {
		this.dataObjectFactory = dataObjectFactory;
	}

}
