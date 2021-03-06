require File.expand_path('../../../spec_helper', __FILE__)

if System.get_property('platform') != 'APPLE'
ruby_version_is "1.9" do
  describe "Math.lgamma" do
    it "returns [Infinity, 1] when passed 0" do
      Math.lgamma(0).should  == [infinity_value, 1]
    end

    it "returns [Infinity, 1] when passed -1" do
      Math.lgamma(-1).should == [infinity_value, 1]
    end

    it "returns [log(sqrt(PI)), 1] when passed 0.5" do
      lg1 = Math.lgamma(0.5)
      lg1[0].should be_close(Math.log(Math.sqrt(Math::PI)), TOLERANCE)
      lg1[1].should == 1
    end

    it "returns [log(2/3*PI, 1] when passed 6.0" do
      lg2 = Math.lgamma(6.0)
      lg2[0].should be_close(Math.log(120.0), TOLERANCE)
      lg2[1].should == 1
    end

    it "returns an approximate value when passed -0.5" do
      lg1 = Math.lgamma(-0.5)
      lg1[0].should be_close(1.2655121, TOLERANCE)
      lg1[1].should == -1
    end

    it "returns an approximate value when passed -1.5" do
      lg2 = Math.lgamma(-1.5)
      lg2[0].should be_close(0.8600470, TOLERANCE)
      lg2[1].should == 1
    end

    ruby_version_is ""..."1.9" do
      it "returns [Infinity, 1] when passed -Infinity" do
        Math.lgamma(-infinity_value).should == [infinity_value, 1]
      end
    end

    ruby_version_is "1.9" do
      it "raises Math::DomainError when passed -Infinity" do
        lambda { Math.lgamma(-infinity_value) }.should raise_error(Math::DomainError)
      end
    end

    # Note: see related issue http://redmine.ruby-lang.org/issues/show/2189
    # If you would like to see simpler Ruby behavior, lobby for Ruby to
    # have platform-independent Math functions.
    ruby_version_is ""..."1.9" do
      platform_is_not :darwin do
        it "returns [Infinity, 1] when passed Infinity" do
          Math.lgamma(-infinity_value).should == [infinity_value, 1]
        end
      end
    end

    # Note: see related issue http://redmine.ruby-lang.org/issues/show/2189
    # If you would like to see simpler Ruby behavior, lobby for Ruby to
    # have platform-independent Math functions.
    ruby_version_is ""..."1.9" do
      platform_is :darwin do
        # JRuby has platform-independent math and behaves as above
        not_compliant_on :jruby do
          it "raises an Errno::EDOM when passed Infinity" do
            lambda { Math.lgamma(infinity_value) }.should raise_error(Errno::EDOM)
          end
        end
      end
    end

    ruby_version_is "1.9" do
      it "returns [Infinity, 1] when passed Infinity" do
        Math.lgamma(infinity_value).should == [infinity_value, 1]
      end
    end

    it "returns [NaN, 1] when passed NaN" do
      Math.lgamma(nan_value)[0].nan?.should be_true
      Math.lgamma(nan_value)[1].should == 1
    end
  end
end
end
